package com.company.idm.infrastructure.rabbitmq;

import java.time.LocalDateTime;

/**
 * 状态心跳消息（协议版本 2）。
 *
 * <p>心跳与业务事件走同一专属队列，以消息类型与协议版本区分；它报告的是身份中台
 * 侧的权威状态与已提交版本头，用于消费方判断新鲜度与是否落后，不携带成员列表与
 * 任何秘密，也不推进消费方已应用的检查点。AMQP 传输层心跳只证明连接存活，
 * 不能替代这里的权威状态心跳。
 *
 * @param protocolVersion 协议版本
 * @param messageType 固定为 HEARTBEAT
 * @param sourceId 稳定来源标识，消费方据此校验来源身份未被替换
 * @param subscriptionId 订阅标识
 * @param scopeType 范围类型：ROLE_GROUP 或 GLOBAL
 * @param roleGroupId 角色组范围标识；全局订阅为空
 * @param status 订阅权威状态（ENABLED/DISABLED/REVOKED）
 * @param committedVersion 该范围已提交版本头；全局订阅为事件游标头
 * @param configVersion 订阅配置控制版本，轮换与生命周期变化时递增
 * @param sentAt 发送时间，消费方据此校验新鲜度并容忍既定时钟误差
 */
public record RoleSupplyHeartbeatMessage(
    int protocolVersion,
    String messageType,
    String sourceId,
    Long subscriptionId,
    String scopeType,
    Long roleGroupId,
    String status,
    Long committedVersion,
    Long configVersion,
    LocalDateTime sentAt
) {

    /** 心跳消息类型。 */
    public static final String TYPE_HEARTBEAT = "HEARTBEAT";
}
