package com.company.idm.infrastructure.rabbitmq;

import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.domain.rolegroup.RoleMembershipChangedEvent;
import com.company.idm.domain.rolegroup.RoleSupplyEventType;
import com.company.idm.infrastructure.config.RabbitmqProperties;
import com.company.idm.infrastructure.config.RoleSupplyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 角色供给事件发布器。
 *
 * <p>事务提交后异步触发（不阻塞业务线程），并由定时任务兜底重试；单实例串行保证按事件顺序投递。
 * 路由键按“范围.角色”分层，使整组动态订阅只需绑定 {@code rg.<组ID>.#} 即可覆盖组内
 * 当前与后续新增角色，无需在角色变化时重新绑定或重签令牌；全局订阅仍按角色绑定，
 * 保持既有对外投递语义。消息经 publisher confirm 确认成功后才标记已发布，失败时保序
 * 停止并在下个周期重试，保持至少一次语义，消费方按 eventId 幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleChangeEventPublisher {

    /** 无角色组归属（全局角色）事件使用的范围标识。 */
    private static final String GLOBAL_SCOPE_SEGMENT = "0";

    private final RoleMembershipChangeRepository changeRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitmqProperties properties;
    private final RoleSupplyProperties supplyProperties;
    private final ObjectMapper objectMapper;
    private final ExecutorService publishExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "role-change-publisher");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean publishQueued = new AtomicBoolean(false);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoleMembershipChanged(RoleMembershipChangedEvent event) {
        schedulePublish();
    }

    /** 异步触发推送：业务线程立即返回，MQ 故障不会拖慢角色变更接口，异常交由下个周期兜底重试。 */
    private void schedulePublish() {
        if (publishQueued.compareAndSet(false, true)) {
            publishExecutor.execute(() -> {
                publishQueued.set(false);
                try {
                    publishPendingChanges();
                } catch (RuntimeException exception) {
                    log.warn("角色供给事件异步推送异常终止，将在下个周期自动重试", exception);
                }
            });
        }
    }

    @Scheduled(fixedDelayString = "${app.rabbitmq.publisher.fixed-delay-ms:5000}")
    public void publishPendingChanges() {
        if (!properties.getPublisher().isEnabled()) {
            return;
        }
        synchronized (this) {
            int batchSize = Math.max(1, properties.getPublisher().getBatchSize());
            while (true) {
                List<RoleMembershipChange> pending = changeRepository.findUnpublished(batchSize);
                if (pending.isEmpty()) {
                    return;
                }
                List<Long> publishedIds = new ArrayList<>();
                for (RoleMembershipChange change : pending) {
                    if (!publish(change)) {
                        markPublished(publishedIds);
                        return;
                    }
                    publishedIds.add(change.id());
                }
                markPublished(publishedIds);
            }
        }
    }

    private void markPublished(List<Long> publishedIds) {
        if (!publishedIds.isEmpty()) {
            changeRepository.markPublished(publishedIds, LocalDateTime.now());
        }
    }

    private boolean publish(RoleMembershipChange change) {
        try {
            String payload = objectMapper.writeValueAsString(
                RoleSupplyMessage.from(change, supplyProperties.getProtocolVersion(),
                    supplyProperties.getSourceId(), objectMapper)
            );
            Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(String.valueOf(change.id()))
                .setHeader("x-idm-message-type", RoleSupplyMessage.TYPE_ROLE_CHANGE)
                .setHeader("x-idm-protocol-version", supplyProperties.getProtocolVersion())
                .build();
            CorrelationData correlation = new CorrelationData(String.valueOf(change.id()));
            rabbitTemplate.send(
                properties.getExchange(),
                routingKeyFor(change),
                message,
                correlation
            );
            CorrelationData.Confirm confirm = correlation.getFuture()
                .get(properties.getPublisher().getConfirmTimeoutMs(), TimeUnit.MILLISECONDS);
            return confirm != null && confirm.isAck();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception exception) {
            log.warn("角色供给事件推送失败，将在下个周期自动重试：eventId={}", change.id(), exception);
            return false;
        }
    }

    /**
     * 计算事件的路由键。
     *
     * <p>形如 {@code rg.<组ID>.role.<角色编码>}、{@code rg.<组ID>.group}、
     * {@code rg.<组ID>.subscription}；无角色组归属的事件使用范围段 0，
     * 使全局订阅可以用 {@code rg.*.role.<编码>} 匹配任意范围的同一角色。
     */
    private String routingKeyFor(RoleMembershipChange change) {
        String scope = change.roleGroupId() == null
            ? GLOBAL_SCOPE_SEGMENT
            : String.valueOf(change.roleGroupId());
        RoleSupplyEventType eventType = change.eventType();
        if (eventType == RoleSupplyEventType.SUBSCRIPTION_CONTROL) {
            return "rg." + scope + ".subscription";
        }
        if (eventType == RoleSupplyEventType.GROUP_UPDATED) {
            return "rg." + scope + ".group";
        }
        if (eventType != null && eventType.name().startsWith("ROLE_")) {
            return "rg." + scope + ".role." + nullSafe(change.roleCode());
        }
        return "rg." + scope + ".role." + nullSafe(change.roleCode());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
