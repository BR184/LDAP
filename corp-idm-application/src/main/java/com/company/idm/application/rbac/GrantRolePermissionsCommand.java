package com.company.idm.application.rbac;

import java.util.List;

public record GrantRolePermissionsCommand(Long roleId, List<Long> permissionIds, String operator) {
}

