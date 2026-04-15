package com.company.idm.application.rbac;

/**
 * 封装创建角色时的应用层命令参数。
 */
public record CreateRoleCommand(String roleCode, String roleName, String remark) {
}

