package com.company.idm.interfaces.role;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 封装角色授权接口的请求参数。
 */
public record GrantRolePermissionsRequest(@NotEmpty(message = "权限列表不能为空") List<Long> permissionIds) {
}

