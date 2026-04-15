package com.company.idm.domain.rbac;

/**
 * 表示角色与资源动作之间的授权策略。
 */
public record RolePolicy(String roleCode, String resourcePath, String action) {
}

