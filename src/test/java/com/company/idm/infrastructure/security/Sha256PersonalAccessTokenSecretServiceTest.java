package com.company.idm.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.idm.application.token.GeneratedPersonalAccessTokenSecret;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Sha256PersonalAccessTokenSecretServiceTest {

    private final Sha256PersonalAccessTokenSecretService service =
        new Sha256PersonalAccessTokenSecretService();

    @Test
    void generatesOpaqueTokensWithA256BitSecret() {
        Set<String> generated = new HashSet<>();

        for (int index = 0; index < 64; index++) {
            GeneratedPersonalAccessTokenSecret secret = service.generate();
            generated.add(secret.rawToken());

            assertThat(secret.rawToken()).startsWith("idm_pat_" + secret.tokenUid() + "_");
            assertThat(secret.rawToken().substring(
                Sha256PersonalAccessTokenSecretService.TOKEN_PREFIX.length()
                    + Sha256PersonalAccessTokenSecretService.TOKEN_UID_LENGTH
                    + 1
            )).hasSize(43);
            assertThat(secret.secretHash()).hasSize(43);
            assertThat(secret.hashVersion()).isEqualTo(1);
            assertThat(service.extractTokenUid(secret.rawToken())).contains(secret.tokenUid());
            assertThat(service.verify(secret.rawToken(), secret.secretHash(), secret.hashVersion())).isTrue();
        }

        assertThat(generated).hasSize(64);
    }

    @Test
    void rejectsMalformedOrMismatchedTokensWithoutThrowing() {
        GeneratedPersonalAccessTokenSecret first = service.generate();
        GeneratedPersonalAccessTokenSecret second = service.generate();

        assertThat(service.verify(first.rawToken(), second.secretHash(), 1)).isFalse();
        assertThat(service.verify(first.rawToken(), first.secretHash(), 2)).isFalse();
        assertThat(service.verify("idm_pat_invalid", first.secretHash(), 1)).isFalse();
        assertThat(service.verify(first.rawToken(), "not-base64!", 1)).isFalse();
        assertThat(service.extractTokenUid(null)).isEmpty();
        assertThat(service.extractTokenUid("idm_pat_invalid")).isEmpty();
    }
}
