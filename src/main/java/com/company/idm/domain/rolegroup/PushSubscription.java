package com.company.idm.domain.rolegroup;

import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 角色变更推送订阅：第三方在平台建立的订阅主体，持有订阅令牌与专属MQ队列。
 *
 * <p>主体类型与订阅令牌保持一致：ROLE_GROUP（订阅本组角色集合）或 GLOBAL（平台管理员订阅全平台角色）。
 * 范围模式决定授权范围的判定方式：整组动态订阅的范围等于该组当前全部角色，显式选集订阅
 * 的范围由选定角色决定。配置版本是订阅生命周期（轮换、停用、启用、撤销）的控制版本，
 * 消费方据此判别控制类事实的新旧，独立于角色组范围的提交有序版本。
 */
@Getter
@Builder(toBuilder = true)
public class PushSubscription {

    private final Long id;
    private final String name;
    private final String description;
    private final PersonalAccessTokenSubjectType subjectType;
    private final Long subjectId;
    private final PushSubscriptionScopeMode scopeMode;
    private final PushSubscriptionStatus status;
    private final Long accessTokenId;
    private final Long configVersion;
    private final String mqQueue;
    private final String mqUsername;
    private final String mqPassword;
    private final String creator;
    private final String modifier;
    private final LocalDateTime gmtCreate;
    private final LocalDateTime gmtModified;

    /** 是否为整组动态范围订阅。 */
    public boolean isDynamicGroupScope() {
        return scopeMode == PushSubscriptionScopeMode.DYNAMIC_GROUP;
    }

    /** 是否为启用状态：只有启用订阅才供应业务数据与推送。 */
    public boolean isEnabled() {
        return status == PushSubscriptionStatus.ENABLED;
    }
}
