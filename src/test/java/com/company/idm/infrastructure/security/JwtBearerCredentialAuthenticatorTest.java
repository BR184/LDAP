package com.company.idm.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.idm.application.auth.ParsedToken;
import com.company.idm.application.auth.TokenService;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserAccessPolicy;
import com.company.idm.domain.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JwtBearerCredentialAuthenticatorTest {

    private final TokenService tokenService = mock(TokenService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtBearerCredentialAuthenticator authenticator = new JwtBearerCredentialAuthenticator(
        tokenService,
        userRepository,
        new UserAccessPolicy()
    );

    @Test
    void preservesTheExistingJwtIdentityAndTokenVersionChecks() {
        when(tokenService.parse("jwt")).thenReturn(new ParsedToken(
            7L,
            "employee",
            3,
            Set.of("STALE_ROLE_CLAIM"),
            Instant.now().plusSeconds(60)
        ));
        when(userRepository.findByUserId("employee")).thenReturn(Optional.of(owner(3)));

        AuthenticatedUser principal = authenticator.authenticate("jwt", "127.0.0.1").orElseThrow();

        assertThat(principal.credentialType()).isEqualTo(CredentialType.SESSION);
        assertThat(principal.credentialId()).isNull();
        assertThat(principal.roleCodes()).containsExactly("CURRENT_ROLE");

        when(userRepository.findByUserId("employee")).thenReturn(Optional.of(owner(4)));
        assertThat(authenticator.authenticate("jwt", "127.0.0.1")).isEmpty();
    }

    private User owner(int tokenVersion) {
        return User.builder()
            .id(7L)
            .userId("employee")
            .tokenVersion(tokenVersion)
            .roleCodes(Set.of("CURRENT_ROLE"))
            .accessAllowed(true)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .build();
    }
}
