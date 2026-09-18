package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.SubscriptionView;
import java.time.LocalDateTime;

/**
 * 订阅列表项响应。
 *
 * @param id 订阅标识
 * @param name 订阅名称（对接方命名）
 * @param description 订阅说明
 * @param subjectType 主体类型：ROLE_GROUP 或 GLOBAL
 * @param subjectId 主体标识（角色组 ID）
 * @param scopeMode 范围模式：DYNAMIC_GROUP 表示订阅整个角色组且随组内角色动态变化
 * @param status 订阅状态
 * @param configVersion 配置控制版本，轮换与生命周期变化时递增
 * @param roleCount 范围内角色数
 * @param creator 创建者
 * @param gmtCreate 创建时间
 * @param gmtModified 修改时间
 */
public record SubscriptionResponse(
    Long id,
    String name,
    String description,
    String subjectType,
    Long subjectId,
    String scopeMode,
    String status,
    Long configVersion,
    int roleCount,
    String creator,
    LocalDateTime gmtCreate,
    LocalDateTime gmtModified
) {

    /** 由订阅视图构造响应。 */
    public static SubscriptionResponse from(SubscriptionView view) {
        return new SubscriptionResponse(
            view.id(),
            view.name(),
            view.description(),
            view.subjectType() == null ? null : view.subjectType().name(),
            view.subjectId(),
            view.scopeMode(),
            view.status() == null ? null : view.status().name(),
            view.configVersion(),
            view.roleCount(),
            view.creator(),
            view.gmtCreate(),
            view.gmtModified()
        );
    }
}
