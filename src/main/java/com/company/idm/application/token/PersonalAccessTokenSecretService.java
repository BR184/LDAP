package com.company.idm.application.token;

import java.util.Optional;

public interface PersonalAccessTokenSecretService {

    GeneratedPersonalAccessTokenSecret generate();

    Optional<String> extractTokenUid(String rawToken);

    boolean verify(String rawToken, String expectedHash, int hashVersion);
}
