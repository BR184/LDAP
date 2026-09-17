package com.company.idm.application.rolegroup;

/**
 * 订阅凭证：订阅令牌（用于 snapshot/changes 接口）+ MQ 连接信息（用于消费推送）。
 */
public record SubscriptionCredential(
    Long subscriptionId,
    String tokenSecret,
    SubscriptionMqInfo mq
) {
}
