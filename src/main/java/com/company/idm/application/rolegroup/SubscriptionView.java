package com.company.idm.application.rolegroup;

import com.company.idm.domain.rolegroup.PushSubscription;
import com.company.idm.domain.rolegroup.PushSubscriptionStatus;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.time.LocalDateTime;

/**
 * 订阅视图：角色组管理页展示订阅列表所需信息。
 *
 * @param id 订阅标识
 * @param name 订阅名称（对接方命名）
 * @param description 订阅说明
 * @param subjectType 主体类型
 * @param subjectId 主体标识（角色组 ID）
 * @param scopeMode 范围模式：整组动态或显式选集
 * @param status 订阅状态
 * @param configVersion 配置控制版本，轮换与生命周期变化时递增
 * @param roleCount 范围内角色数：整组动态订阅为组内当前角色数
 * @param creator 创建者
 * @param gmtCreate 创建时间
 * @param gmtModified 修改时间
 */
public record SubscriptionView(
    Long id,
    String name,
    String description,
    PersonalAccessTokenSubjectType subjectType,
    Long subjectId,
    String scopeMode,
    PushSubscriptionStatus status,
    Long configVersion,
    int roleCount,
    String creator,
    LocalDateTime gmtCreate,
    LocalDateTime gmtModified
) {

    /** 由订阅领域对象与范围内角色数构造视图。 */
    public static SubscriptionView of(PushSubscription subscription, int roleCount) {
        return new SubscriptionView(
            subscription.getId(),
            subscription.getName(),
            subscription.getDescription(),
            subscription.getSubjectType(),
            subscription.getSubjectId(),
            subscription.getScopeMode() == null ? null : subscription.getScopeMode().name(),
            subscription.getStatus(),
            subscription.getConfigVersion(),
            roleCount,
            subscription.getCreator(),
            subscription.getGmtCreate(),
            subscription.getGmtModified()
        );
    }
}
