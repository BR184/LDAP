package com.company.idm.application.rbac;

import java.util.List;

public record RolePermissionBundle(
    String id,
    String name,
    int sort,
    List<Long> permissionIds,
    List<String> permissionCodes,
    String categoryId,
    String categoryName,
    String categoryDescription,
    int categorySort,
    RolePermissionBundleTier tier
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
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("Role permission bundle category id must not be blank");
        }
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("Role permission bundle category name must not be blank");
        }
        if (categoryDescription == null || categoryDescription.isBlank()) {
            throw new IllegalArgumentException("Role permission bundle category description must not be blank");
        }
        if (tier == null) {
            throw new IllegalArgumentException("Role permission bundle tier must not be null");
        }
        permissionIds = List.copyOf(permissionIds);
        permissionCodes = List.copyOf(permissionCodes);
    }
}
