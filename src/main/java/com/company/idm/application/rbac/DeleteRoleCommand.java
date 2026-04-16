package com.company.idm.application.rbac;

/**
 * 封装删除角色时的应用层命令参数。
 */
public record DeleteRoleCommand(Long roleId, String operator) {
}

