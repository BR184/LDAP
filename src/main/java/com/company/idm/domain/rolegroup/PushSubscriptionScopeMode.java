package com.company.idm.domain.rolegroup;

/**
 * 订阅范围模式。
 *
 * <p>{@link #DYNAMIC_GROUP} 表示订阅整个角色组：范围等于该组当前全部角色，
 * 组内新增角色自动纳入、删除角色自动移出，无需修改队列绑定或重签令牌。
 * {@link #SELECTED_ROLES} 表示显式角色选集，用于全局订阅等需要人工挑选范围的场景，
 * 其授权范围与投递语义保持不变，不因整组动态订阅的引入而扩大。
 */
public enum PushSubscriptionScopeMode {

    /** 整组动态范围：随角色组当前角色集合变化。 */
    DYNAMIC_GROUP,

    /** 显式角色选集：范围由创建/更新时选定的角色决定。 */
    SELECTED_ROLES
}
