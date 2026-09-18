package com.company.idm.domain.rolegroup;

/**
 * 角色供给事件类型：覆盖对外授权事实的全部变化来源。
 *
 * <p>成员类事件承载角色与成员身份；角色类事件承载角色目录变化（含删除事实，
 * 删除后仍可按发生时所属范围重放）；组类事件承载角色组展示信息变化；
 * 订阅控制类事件承载停用/启用/撤销/轮换等生命周期变化，使用独立的控制版本，
 * 不参与组范围版本序列，避免被组快照边界吞掉。
 */
public enum RoleSupplyEventType {

    /** 成员加入角色。 */
    MEMBER_ADDED,

    /** 成员退出角色（含用户删除、停用导致的整体退出）。 */
    MEMBER_REMOVED,

    /** 角色创建。 */
    ROLE_CREATED,

    /** 角色展示信息（名称/备注）变化。 */
    ROLE_UPDATED,

    /** 角色启用/停用状态变化。 */
    ROLE_STATUS_CHANGED,

    /** 角色删除；删除事实必须可被重放，不因角色已不存在而查不到。 */
    ROLE_DELETED,

    /** 角色组展示信息变化。 */
    GROUP_UPDATED,

    /** 订阅生命周期控制变化（停用/启用/撤销/轮换）。 */
    SUBSCRIPTION_CONTROL
}
