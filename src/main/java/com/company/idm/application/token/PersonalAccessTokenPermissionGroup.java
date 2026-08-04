package com.company.idm.application.token;

import java.util.List;

public record PersonalAccessTokenPermissionGroup(
    String id,
    String name,
    PersonalAccessTokenPermissionRisk risk,
    int sort,
    List<String> permissionCodes
) {

    public PersonalAccessTokenPermissionGroup {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Access token permission group id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Access token permission group name must not be blank");
        }
        if (risk == null) {
            throw new IllegalArgumentException("Access token permission group risk must not be null");
        }
        if (permissionCodes == null || permissionCodes.isEmpty()
            || permissionCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("Access token permission group must contain permission codes");
        }
        permissionCodes = List.copyOf(permissionCodes);
    }
}
