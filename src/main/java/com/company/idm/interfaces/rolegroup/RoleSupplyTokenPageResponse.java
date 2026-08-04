package com.company.idm.interfaces.rolegroup;

import java.util.List;

public record RoleSupplyTokenPageResponse(
    List<RoleSupplyTokenResponse> items,
    long total,
    int page,
    int pageSize
) {
}
