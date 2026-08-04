package com.company.idm.interfaces.auth;

import java.util.List;

public record CurrentCapabilitiesResponse(List<String> permissionCodes) {

    public CurrentCapabilitiesResponse {
        permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
    }
}
