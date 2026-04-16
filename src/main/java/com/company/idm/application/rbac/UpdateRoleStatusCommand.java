package com.company.idm.application.rbac;

/**
 * 封装更新角色状态时的应用层命令参数。
 */
public record UpdateRoleStatusCommand(Long roleId, Integer status, String operator) {
}

