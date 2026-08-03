package com.company.idm.interfaces.token;

import java.util.List;

public record PersonalAccessTokenPageResponse(
    List<PersonalAccessTokenResponse> items,
    long total,
    int page,
    int pageSize
) {
}
