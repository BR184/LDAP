package com.company.idm.application.rolegroup;

/**
 * 展示给第三方的 MQ 连接信息；密码在轮换或重看凭证时一次性展示。
 */
public record SubscriptionMqInfo(
    String host,
    int port,
    String vhost,
    String queue,
    String username,
    String password
) {
}
