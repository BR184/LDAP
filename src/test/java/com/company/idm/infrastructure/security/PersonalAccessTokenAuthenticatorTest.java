package com.company.idm.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.idm.application.token.PersonalAccessTokenSecretService;
import com.company.idm.application.token.PersonalAccessTokenUsageService;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenPermission;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserAccessPolicy;
import com.company.idm.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonalAccessTokenAuthenticatorTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-03T10:00:00Z"),
        ZoneId.of("Asia/Shanghai")
    );

    private final PersonalAccessTokenSecretService secretService = mock(PersonalAccessTokenSecretService.class);
    private final PersonalAccessTokenRepository tokenRepository = mock(PersonalAccessTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PersonalAccessTokenUsageService usageService = mock(PersonalAccessTokenUsageService.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final PersonalAccessTokenAuthenticator authenticator = new PersonalAccessTokenAuthenticator(
        secretService,
        tokenRepository,
        userRepository,
        new UserAccessPolicy(),
        usageService,
        auditLogRepository,
        CLOCK
    );

    @Test
    void authenticatesAnActiveTokenForAnEnabledEmployee() {
        String rawToken = "raw";
        PersonalAccessToken token = activeToken();
        when(secretService.extractTokenUid(rawToken)).thenReturn(Optional.of("uid"));
        when(tokenRepository.findByTokenUid("uid")).thenReturn(Optional.of(token));
        when(secretService.verify(rawToken, "hash", 1)).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(activeOwner()));

        Optional<AuthenticatedUser> result = authenticator.authenticate(rawToken, "127.0.0.1");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().credentialType()).isEqualTo(CredentialType.PERSONAL_ACCESS_TOKEN);
        assertThat(result.orElseThrow().credentialId()).isEqualTo(11L);
        assertThat(result.orElseThrow().selectedPermissionCodes()).containsExactly("USER_READ");
    }

    @Test
    void rejectsMalformedRevokedAndExpiredTokens() {
        when(secretService.extractTokenUid("malformed")).thenReturn(Optional.empty());
        assertThat(authenticator.authenticate("malformed", "127.0.0.1")).isEmpty();
        verifyNoInteractions(tokenRepository, userRepository);

        when(secretService.extractTokenUid("revoked")).thenReturn(Optional.of("revoked-id"));
        when(tokenRepository.findByTokenUid("revoked-id")).thenReturn(Optional.of(
            activeToken().toBuilder().revokedAt(now().minusMinutes(1)).build()
        ));
        assertThat(authenticator.authenticate("revoked", "127.0.0.1")).isEmpty();

        when(secretService.extractTokenUid("expired")).thenReturn(Optional.of("expired-id"));
        when(tokenRepository.findByTokenUid("expired-id")).thenReturn(Optional.of(
            activeToken().toBuilder().expiresAt(now().minusSeconds(1)).build()
        ));
        assertThat(authenticator.authenticate("expired", "127.0.0.1")).isEmpty();
    }

    @Test
    void rejectsDisabledOrResignedOwners() {
        String rawToken = "raw";
        when(secretService.extractTokenUid(rawToken)).thenReturn(Optional.of("uid"));
        when(tokenRepository.findByTokenUid("uid")).thenReturn(Optional.of(activeToken()));
        when(secretService.verify(rawToken, "hash", 1)).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(activeOwner().toBuilder()
            .accessAllowed(false)
            .employmentStatus(EmploymentStatus.RESIGNED)
            .build()));

        assertThat(authenticator.authenticate(rawToken, "127.0.0.1")).isEmpty();
    }

    @Test
    void authenticatesRoleGroupTokensWithoutInheritingAUserIdentity() {
        String rawToken = "group-token";
        PersonalAccessToken token = activeToken().toBuilder()
            .userId(null)
            .subjectType(PersonalAccessTokenSubjectType.ROLE_GROUP)
            .subjectId(19L)
            .permissions(List.of())
            .build();
        when(secretService.extractTokenUid(rawToken)).thenReturn(Optional.of("uid"));
        when(tokenRepository.findByTokenUid("uid")).thenReturn(Optional.of(token));
        when(secretService.verify(rawToken, "hash", 1)).thenReturn(true);

        AuthenticatedUser principal = authenticator.authenticate(rawToken, "127.0.0.1").orElseThrow();

        assertThat(principal.id()).isNull();
        assertThat(principal.userId()).isEqualTo("role-supply:group:19");
        assertThat(principal.roleCodes()).isEmpty();
        assertThat(principal.selectedPermissionCodes()).isEmpty();
        assertThat(principal.tokenSubjectType()).isEqualTo(PersonalAccessTokenSubjectType.ROLE_GROUP);
        assertThat(principal.tokenSubjectId()).isEqualTo(19L);
        verifyNoInteractions(userRepository);
    }

    private PersonalAccessToken activeToken() {
        return PersonalAccessToken.builder()
            .id(11L)
            .tokenUid("uid")
            .userId(7L)
            .secretHash("hash")
            .hashVersion(1)
            .expiresAt(now().plusDays(1))
            .permissions(List.of(new PersonalAccessTokenPermission(
                1L,
                "USER_READ",
                "用户查询",
                "/api/v1/users",
                "GET"
            )))
            .build();
    }

    private User activeOwner() {
        return User.builder()
            .id(7L)
            .userId("employee")
            .tokenVersion(3)
            .roleCodes(Set.of("EMPLOYEE"))
            .accessAllowed(true)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .build();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CLOCK);
    }
}
