package com.company.idm.application.rolegroup;

import com.company.idm.domain.rbac.RoleScope;
import java.util.List;

/**
 * 快照中的单个角色事实。
 *
 * <p>角色以稳定标识识别：同编码删除再建是新角色，消费方不得继承旧角色的事件水位。
 * 成员仅以平台用户 ID 匹配（等于 LDAP uid 与下游登录名），姓名只作展示。
 *
 * @param roleId 角色稳定标识
 * @param roleCode 角色编码（业务映射契约）
 * @param roleName 角色名称
 * @param roleScope 角色作用域
 * @param roleGroupId 所属角色组
 * @param roleStatus 角色状态（1 启用 / 0 停用）
 * @param memberNames 成员姓名，与 memberUserIds 同源同序
 * @param memberUserIds 成员平台用户 ID，与 memberNames 同源同序
 */
public record RoleSupplyRoleSnapshot(
    Long roleId,
    String roleCode,
    String roleName,
    RoleScope roleScope,
    Long roleGroupId,
    Integer roleStatus,
    List<String> memberNames,
    List<String> memberUserIds
) {
}
