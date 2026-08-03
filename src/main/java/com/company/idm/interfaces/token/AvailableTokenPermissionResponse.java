package com.company.idm.interfaces.token;

import com.company.idm.domain.rbac.Permission;

public record AvailableTokenPermissionResponse(
    Long id,
    String permissionCode,
    String permissionName,
    String resourcePath,
    String action
) {

    public static AvailableTokenPermissionResponse from(Permission permission) {
        return new AvailableTokenPermissionResponse(
            permission.getId(),
            permission.getPermissionCode(),
            permission.getPermissionName(),
            permission.getResourcePath(),
            permission.getAction()
        );
    }
}
