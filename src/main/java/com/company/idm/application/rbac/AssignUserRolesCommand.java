package com.company.idm.application.rbac;

import java.util.List;

/**
 * 封装用户分配角色时的应用层命令参数。
 */
public record AssignUserRolesCommand(
    Long userId,
    List<Long> roleIds,
    List<Long> expectedRoleIds,
    String operator
) {
}
