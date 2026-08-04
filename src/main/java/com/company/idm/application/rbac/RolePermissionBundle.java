package com.company.idm.application.rbac;

import java.util.List;

public record RolePermissionBundle(
    String id,
    String name,
    int sort,
    List<Long> permissionIds,
    List<String> permissionCodes
) {

    public RolePermissionBundle {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Role permission bundle id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role permission bundle name must not be blank");
        }
        if (permissionIds == null || permissionIds.isEmpty()) {
            throw new IllegalArgumentException("Role permission bundle must contain permission ids");
        }
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            throw new IllegalArgumentException("Role permission bundle must contain permission codes");
        }
        permissionIds = List.copyOf(permissionIds);
        permissionCodes = List.copyOf(permissionCodes);
    }
}
