package com.company.idm.infrastructure.rabbitmq;

import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.domain.rolegroup.RoleMembershipChangedEvent;
import com.company.idm.infrastructure.config.RabbitmqProperties;
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
 * 角色成员变更事件发布器。
 * 事务提交后异步触发（不阻塞业务线程），并由定时任务兜底；单实例串行保证按事件顺序投递。
 * 消息经 publisher confirm 确认成功后才标记已发布，失败时保序停止并在下个周期自动重试，
 * 与开放接口一致保持至少一次语义，第三方按 eventId 幂等消费。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleChangeEventPublisher {

    private static final String ROUTING_KEY_PREFIX = "role.";

    private final RoleMembershipChangeRepository changeRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitmqProperties properties;
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
                    log.warn("角色变更事件异步推送异常终止，将在下个周期自动重试", exception);
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
            String payload = objectMapper.writeValueAsString(RoleChangeMessage.from(change));
            Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(String.valueOf(change.id()))
                .build();
            CorrelationData correlation = new CorrelationData(String.valueOf(change.id()));
            rabbitTemplate.send(
                properties.getExchange(),
                ROUTING_KEY_PREFIX + change.roleCode(),
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
            log.warn("角色变更事件推送失败，将在下个周期自动重试：eventId={}", change.id(), exception);
            return false;
        }
    }
}
