package com.company.idm.infrastructure.rabbitmq;

import com.company.idm.infrastructure.config.RabbitmqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 角色变更推送拓扑初始化：应用启动时声明交换机与死信队列。
 * 避免无订阅期间事件因交换机缺失而持续积压，保证订阅者创建后只会收到其建立订阅之后的事件。
 * 消息服务不可用时仅告警、不阻断启动，创建订阅时会再次幂等声明补齐。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitTopologyInitializer implements ApplicationRunner {

    private final RabbitManagementClient rabbitManagementClient;
    private final RabbitmqProperties rabbitmqProperties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            rabbitManagementClient.ensureTopicExchange(rabbitmqProperties.getExchange());
            rabbitManagementClient.ensureFanoutExchange(rabbitmqProperties.getDeadLetterExchange());
            rabbitManagementClient.ensureQueue(rabbitmqProperties.getDeadLetterQueue(), null);
            rabbitManagementClient.bindQueue(
                rabbitmqProperties.getDeadLetterQueue(),
                rabbitmqProperties.getDeadLetterExchange(),
                ""
            );
            log.info("角色变更推送拓扑已就绪：exchange={}", rabbitmqProperties.getExchange());
        } catch (RuntimeException exception) {
            log.warn("角色变更推送拓扑初始化未完成，将在创建订阅时自动补齐：{}", exception.getMessage());
        }
    }
}
