package com.company.idm.application.token;

import com.company.idm.domain.token.PersonalAccessToken;

public record CreatedPersonalAccessToken(PersonalAccessToken token, String secret) {
}
