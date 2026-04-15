package com.company.idm.interfaces.role;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record GrantRolePermissionsRequest(@NotEmpty(message = "权限列表不能为空") List<Long> permissionIds) {
}

