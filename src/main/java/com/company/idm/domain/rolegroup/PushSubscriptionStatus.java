package com.company.idm.domain.rolegroup;

/**
 * 推送订阅状态：停用后队列继续积压消息，恢复后第三方可续收。
 */
public enum PushSubscriptionStatus {
    ENABLED,
    DISABLED
}
