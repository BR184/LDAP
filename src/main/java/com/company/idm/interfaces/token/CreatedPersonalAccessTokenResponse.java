package com.company.idm.interfaces.token;

public record CreatedPersonalAccessTokenResponse(
    PersonalAccessTokenResponse token,
    String secret
) {
}
