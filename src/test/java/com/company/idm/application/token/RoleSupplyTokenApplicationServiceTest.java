package com.company.idm.application.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.user.PasswordVerificationTokenService;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.domain.token.PersonalAccessTokenScopeMode;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.PersonalAccessTokenProperties;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleSupplyTokenApplicationServiceTest {

    private final PersonalAccessTokenRepository tokenRepository = mock(PersonalAccessTokenRepository.class);
    private final PersonalAccessTokenSecretService secretService = mock(PersonalAccessTokenSecretService.class);
    private final PersonalAccessTokenProperties properties = new PersonalAccessTokenProperties();
    private final RoleSupplyTokenApplicationService service = new RoleSupplyTokenApplicationService(
        tokenRepository,
        secretService,
        properties,
        mock(UserRepository.class),
        mock(PasswordVerificationTokenService.class),
        mock(AuditLogRepository.class)
    );

    @BeforeEach
    void setUp() {
        when(tokenRepository.countActiveBySubject(
            any(PersonalAccessTokenSubjectType.class), any(), any(LocalDateTime.class)
        )).thenReturn(0L);
        when(secretService.generate()).thenReturn(new GeneratedPersonalAccessTokenSecret(
            "uid", "idm_pat_uid_secret", "hash", 1, "idm_pat_uid_..."
        ));
        when(tokenRepository.create(any())).thenAnswer(invocation -> invocation.<PersonalAccessToken>getArgument(0)
            .toBuilder()
            .id(80L)
            .build());
    }

    @Test
    void createsAGroupSubjectTokenWithoutUserPermissions() {
        CreatedPersonalAccessToken result = service.createTokenFor(
            PersonalAccessTokenSubjectType.ROLE_GROUP,
            10L,
            "财务系统同步",
            "只读角色供给",
            session(),
            "127.0.0.1"
        );

        assertThat(result.secret()).isEqualTo("idm_pat_uid_secret");
        assertThat(result.token().getSubjectType()).isEqualTo(PersonalAccessTokenSubjectType.ROLE_GROUP);
        assertThat(result.token().getSubjectId()).isEqualTo(10L);
        assertThat(result.token().getUserId()).isNull();
        assertThat(result.token().getScopeMode()).isEqualTo(PersonalAccessTokenScopeMode.FIXED);
        assertThat(result.token().getPermissions()).isEmpty();

        ArgumentCaptor<PersonalAccessToken> tokenCaptor = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(tokenRepository).create(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getSecretValue()).isEqualTo("idm_pat_uid_secret");
        assertThat(tokenCaptor.getValue().getExpiresAt()).isNull();
    }

    @Test
    void creatingTokensFromTokenCredentialsIsRejected() {
        AuthenticatedUser groupToken = new AuthenticatedUser(
            null,
            "role-supply:group:10",
            0,
            Set.of(),
            CredentialType.PERSONAL_ACCESS_TOKEN,
            80L,
            Set.of(),
            PersonalAccessTokenScopeMode.FIXED,
            PersonalAccessTokenSubjectType.ROLE_GROUP,
            10L
        );

        assertThatThrownBy(() -> service.createTokenFor(
            PersonalAccessTokenSubjectType.ROLE_GROUP, 10L, "财务系统同步", null, groupToken, "127.0.0.1"
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void activeTokenLimitIsEnforcedPerSubject() {
        when(tokenRepository.countActiveBySubject(
            eq(PersonalAccessTokenSubjectType.GLOBAL), eq(null), any(LocalDateTime.class)
        )).thenReturn((long) properties.getMaxActivePerUser());

        assertThatThrownBy(() -> service.createTokenFor(
            PersonalAccessTokenSubjectType.GLOBAL, null, "全平台订阅", null, session(), "127.0.0.1"
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PAT_ACTIVE_LIMIT_EXCEEDED");
    }

    @Test
    void deleteTokenIsIdempotentWhenTheRecordIsAlreadyGone() {
        when(tokenRepository.delete(80L, PersonalAccessTokenSubjectType.ROLE_GROUP, 10L)).thenReturn(false);

        service.deleteToken(80L, PersonalAccessTokenSubjectType.ROLE_GROUP, 10L, session(), "127.0.0.1");

        verify(tokenRepository).delete(80L, PersonalAccessTokenSubjectType.ROLE_GROUP, 10L);
    }

    @Test
    void revokeTokenIsANoOpWhenTheTokenWasAlreadyRevoked() {
        when(tokenRepository.findByIdAndSubject(80L, PersonalAccessTokenSubjectType.ROLE_GROUP, 10L))
            .thenReturn(Optional.of(PersonalAccessToken.builder()
                .id(80L)
                .subjectType(PersonalAccessTokenSubjectType.ROLE_GROUP)
                .subjectId(10L)
                .revokedAt(LocalDateTime.now())
                .build()));

        service.revokeToken(80L, PersonalAccessTokenSubjectType.ROLE_GROUP, 10L, session(), "127.0.0.1");

        verify(tokenRepository, never()).revoke(eq(80L), any(), any(), any(), any());
    }

    private AuthenticatedUser session() {
        return new AuthenticatedUser(
            7L,
            "delegate",
            0,
            Set.of("NORMAL_USER"),
            CredentialType.SESSION,
            null,
            Set.of(),
            null,
            PersonalAccessTokenSubjectType.USER,
            7L
        );
    }
}
