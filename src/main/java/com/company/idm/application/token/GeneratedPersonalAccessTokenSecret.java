package com.company.idm.application.token;

public record GeneratedPersonalAccessTokenSecret(
    String tokenUid,
    String rawToken,
    String secretHash,
    int hashVersion,
    String displayPrefix
) {
}
