package com.company.idm.application.rbac;

import java.util.List;

/**
 * 封装角色授权时的角色与权限参数。
 */
public record GrantRolePermissionsCommand(
    Long roleId,
    List<Long> permissionIds,
    List<Long> expectedPermissionIds,
    String operator
) {
}

