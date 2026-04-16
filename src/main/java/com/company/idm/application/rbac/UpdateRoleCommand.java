package com.company.idm.application.rbac;

/**
 * 封装更新角色时的应用层命令参数。
 */
public record UpdateRoleCommand(Long roleId, String roleName, Integer permissionLevel, String remark) {
}

