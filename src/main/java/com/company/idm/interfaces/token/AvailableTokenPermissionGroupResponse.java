package com.company.idm.interfaces.token;

import com.company.idm.application.token.AvailablePersonalAccessTokenPermissionGroup;

public record AvailableTokenPermissionGroupResponse(
    String id,
    String name,
    String risk,
    int sort,
    java.util.List<AvailableTokenPermissionResponse> permissions
) {

    public static AvailableTokenPermissionGroupResponse from(
        AvailablePersonalAccessTokenPermissionGroup group
    ) {
        return new AvailableTokenPermissionGroupResponse(
            group.group().id(),
            group.group().name(),
            group.group().risk().name(),
            group.group().sort(),
            group.permissions().stream().map(AvailableTokenPermissionResponse::from).toList()
        );
    }
}
