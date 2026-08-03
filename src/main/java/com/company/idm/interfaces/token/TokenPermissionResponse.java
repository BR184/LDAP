package com.company.idm.interfaces.token;

import com.company.idm.domain.token.PersonalAccessTokenPermission;

public record TokenPermissionResponse(
    Long id,
    String permissionCode,
    String permissionName,
    String resourcePath,
    String action
) {

    public static TokenPermissionResponse from(PersonalAccessTokenPermission permission) {
        return new TokenPermissionResponse(
            permission.id(),
            permission.code(),
            permission.name(),
            permission.resourcePath(),
            permission.action()
        );
    }
}
