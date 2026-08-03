package com.company.idm.domain.rbac;

/**
 * 表示角色与稳定权限编码之间的授权策略。
 */
public record RolePolicy(String roleCode, String permissionCode) {
}

