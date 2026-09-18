package com.company.idm.infrastructure.rabbitmq;

import com.company.idm.domain.rolegroup.PushSubscription;
import com.company.idm.domain.rolegroup.PushSubscriptionRepository;
import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.domain.rolegroup.RoleScopeVersionRepository;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.config.RabbitmqProperties;
import com.company.idm.infrastructure.config.RoleSupplyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订阅状态心跳发布器。
 *
 * <p>周期性向每个启用订阅的专属队列投递权威状态与已提交版本头，使消费方无需轮询角色数据
 * 即可判断“新鲜度”和“是否落后”：远端版本领先而事件未到时，消费方触发一次有界补偿。
 * 心跳经默认交换机直投队列，不占用业务路由规则，也不需要给消费账号任何写权限；
 * 它不携带成员列表与秘密，也不推进消费方已应用的检查点。单个订阅投递失败只记录告警，
 * 下个周期自动重试，不影响其他订阅。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleSupplyHeartbeatPublisher {

    private final PushSubscriptionRepository subscriptionRepository;
    private final RoleScopeVersionRepository scopeVersionRepository;
    private final RoleMembershipChangeRepository changeRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitmqProperties rabbitmqProperties;
    private final RoleSupplyProperties supplyProperties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.role-supply.heartbeat-interval-ms:30000}")
    public void publishHeartbeats() {
        if (!rabbitmqProperties.getPublisher().isEnabled()) {
            return;
        }
        for (PushSubscription subscription : subscriptionRepository.findAllEnabled()) {
            publishFor(subscription);
        }
    }

    /**
     * 向指定订阅投递一次状态心跳。
     *
     * @param subscription 目标订阅；队列缺失或投递失败时静默跳过并告警
     */
    public void publishFor(PushSubscription subscription) {
        String queue = subscription.getMqQueue();
        if (queue == null || queue.isBlank()) {
            return;
        }
        try {
            Long groupId = subscription.getSubjectType() == PersonalAccessTokenSubjectType.ROLE_GROUP
                ? subscription.getSubjectId()
                : null;
            long committedVersion = groupId == null
                ? changeRepository.currentCursor()
                : scopeVersionRepository.currentVersion(groupId);
            RoleSupplyHeartbeatMessage heartbeat = new RoleSupplyHeartbeatMessage(
                supplyProperties.getProtocolVersion(),
                RoleSupplyHeartbeatMessage.TYPE_HEARTBEAT,
                supplyProperties.getSourceId(),
                subscription.getId(),
                subscription.getSubjectType().name(),
                groupId,
                subscription.getStatus().name(),
                committedVersion,
                subscription.getConfigVersion(),
                LocalDateTime.now()
            );
            Message message = MessageBuilder
                .withBody(objectMapper.writeValueAsString(heartbeat).getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setHeader("x-idm-message-type", RoleSupplyHeartbeatMessage.TYPE_HEARTBEAT)
                .setHeader("x-idm-protocol-version", supplyProperties.getProtocolVersion())
                .build();
            rabbitTemplate.send("", queue, message);
        } catch (Exception exception) {
            log.warn("订阅状态心跳投递失败，将在下个周期重试：subscriptionId={}", subscription.getId(), exception);
        }
    }
}
