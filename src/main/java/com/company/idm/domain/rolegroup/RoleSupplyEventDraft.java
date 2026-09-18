package com.company.idm.domain.rolegroup;

import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleScope;
import java.util.Map;

/**
 * 角色供给事件草稿：各授权写入点统一通过它描述“对外授权事实发生了什么变化”。
 *
 * <p>草稿只承载事实本身，范围版本分配、落库与发布唤醒由记录器在业务事务内完成，
 * 因此调用方无需关心版本机制，也不会出现“业务已提交但事件缺失”的双轨写入。
 *
 * @param eventType 事件类型
 * @param roleId 角色稳定标识
 * @param roleCode 角色编码快照
 * @param roleName 角色名称快照
 * @param roleScope 角色作用域快照
 * @param roleGroupId 事件所属角色组范围；全局角色事件为空
 * @param userId 成员用户主键
 * @param memberName 成员姓名快照
 * @param memberUserId 成员平台用户 ID（LDAP uid / 下游登录名）
 * @param changeType 成员变更类型 ADDED/REMOVED；非成员事件为空
 * @param payload 结构化变更内容
 * @param subscriptionId 订阅控制事件的目标订阅
 * @param controlVersion 订阅控制版本
 * @param operator 操作者标识
 */
public record RoleSupplyEventDraft(
    RoleSupplyEventType eventType,
    Long roleId,
    String roleCode,
    String roleName,
    RoleScope roleScope,
    Long roleGroupId,
    Long userId,
    String memberName,
    String memberUserId,
    String changeType,
    Map<String, Object> payload,
    Long subscriptionId,
    Long controlVersion,
    String operator
) {

    /** 成员加入或退出角色的事件草稿。 */
    public static RoleSupplyEventDraft memberChange(
        Role role,
        Long userId,
        String memberName,
        String memberUserId,
        boolean added,
        String operator
    ) {
        return new RoleSupplyEventDraft(
            added ? RoleSupplyEventType.MEMBER_ADDED : RoleSupplyEventType.MEMBER_REMOVED,
            role.getId(),
            role.getRoleCode(),
            role.getRoleName(),
            role.getRoleScope(),
            role.getRoleGroupId(),
            userId,
            memberName,
            memberUserId,
            added ? "ADDED" : "REMOVED",
            Map.of("roleStatus", String.valueOf(role.getStatus())),
            null,
            null,
            operator
        );
    }

    /**
     * 成员变化事件草稿（原始值形式）。
     *
     * <p>供仓储层在已持有角色与用户快照时直接构造，避免为记录事件再做一次领域对象转换。
     */
    public static RoleSupplyEventDraft memberChange(
        RoleSupplyEventType eventType,
        Long roleId,
        String roleCode,
        String roleName,
        RoleScope roleScope,
        Long roleGroupId,
        Long userId,
        String memberName,
        String memberUserId,
        String changeType,
        Map<String, Object> payload,
        String operator
    ) {
        return new RoleSupplyEventDraft(
            eventType,
            roleId,
            roleCode,
            roleName,
            roleScope,
            roleGroupId,
            userId,
            memberName,
            memberUserId,
            changeType,
            payload,
            null,
            null,
            operator
        );
    }

    /** 角色目录变化（创建、展示信息、状态、删除）的事件草稿；删除事实按发生时所属范围保留。 */
    public static RoleSupplyEventDraft roleEvent(
        Role role,
        RoleSupplyEventType eventType,
        Map<String, Object> payload,
        String operator
    ) {
        return new RoleSupplyEventDraft(
            eventType,
            role.getId(),
            role.getRoleCode(),
            role.getRoleName(),
            role.getRoleScope(),
            role.getRoleGroupId(),
            null,
            null,
            null,
            null,
            payload,
            null,
            null,
            operator
        );
    }

    /** 角色组展示信息变化的事件草稿。 */
    public static RoleSupplyEventDraft groupEvent(
        Long groupId,
        String groupName,
        Map<String, Object> payload,
        String operator
    ) {
        return new RoleSupplyEventDraft(
            RoleSupplyEventType.GROUP_UPDATED,
            null,
            null,
            null,
            null,
            groupId,
            null,
            null,
            null,
            null,
            payload == null ? Map.of("groupName", String.valueOf(groupName)) : payload,
            null,
            null,
            operator
        );
    }

    /**
     * 订阅生命周期控制事件草稿。
     *
     * <p>控制事件归属于订阅所在范围以便按范围路由，但不占用组范围版本序列，
     * 由独立的控制版本判定新旧，避免被组快照边界吞掉。
     */
    public static RoleSupplyEventDraft subscriptionControl(
        Long subscriptionId,
        Long groupId,
        String action,
        long controlVersion,
        String operator
    ) {
        return new RoleSupplyEventDraft(
            RoleSupplyEventType.SUBSCRIPTION_CONTROL,
            null,
            null,
            null,
            null,
            groupId,
            null,
            null,
            null,
            null,
            Map.of("action", action, "controlVersion", controlVersion),
            subscriptionId,
            controlVersion,
            operator
        );
    }
}
