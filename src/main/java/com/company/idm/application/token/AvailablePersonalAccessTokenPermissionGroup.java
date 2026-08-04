package com.company.idm.application.token;

import com.company.idm.domain.rbac.Permission;
import java.util.List;

public record AvailablePersonalAccessTokenPermissionGroup(
    PersonalAccessTokenPermissionGroup group,
    List<Permission> permissions
) {

    public AvailablePersonalAccessTokenPermissionGroup {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
