package com.company.idm.domain.rolegroup;

/**
 * 推送订阅状态。
 *
 * <p>{@link #ENABLED} 正常供应数据与推送；{@link #DISABLED} 停止供应但保留队列积压，
 * 恢复后消费方可续收；{@link #REVOKED} 是删除/撤销后的终态——保留订阅行作为
 * “旧连接与旧请求不得继续生效”的终态凭据，同时清除敏感 MQ 密码明文。
 */
public enum PushSubscriptionStatus {
    ENABLED,
    DISABLED,
    REVOKED
}
