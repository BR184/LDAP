package com.company.idm.interfaces.role;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 封装角色授权接口的请求参数。
 */
public record GrantRolePermissionsRequest(
    @NotEmpty(message = "权限列表不能为空") List<@NotNull(message = "权限ID不能为空") Long> permissionIds,
    List<@NotNull(message = "预期权限ID不能为空") Long> expectedPermissionIds
) {
}

