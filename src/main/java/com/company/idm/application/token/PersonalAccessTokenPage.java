package com.company.idm.application.token;

import com.company.idm.domain.token.PersonalAccessToken;
import java.util.List;

public record PersonalAccessTokenPage(
    List<PersonalAccessToken> items,
    long total,
    int page,
    int pageSize
) {
}
