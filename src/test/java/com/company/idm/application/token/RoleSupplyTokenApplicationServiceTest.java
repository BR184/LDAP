package com.company.idm.application.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rolegroup.RoleGroupAuthorizationService;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleSupplyTokenApplicationServiceTest {

    private final PersonalAccessTokenRepository tokenRepository = mock(PersonalAccessTokenRepository.class);
    private final PersonalAccessTokenSecretService secretService = mock(PersonalAccessTokenSecretService.class);
    private final RoleGroupAuthorizationService authorizationService = mock(RoleGroupAuthorizationService.class);
    private final RoleSupplyTokenApplicationService service = new RoleSupplyTokenApplicationService(
        tokenRepository,
        secretService,
        new PersonalAccessTokenProperties(),
        authorizationService,
        mock(UserRepository.class),
        mock(PasswordVerificationTokenService.class),
        mock(AuditLogRepository.class)
    );

    @BeforeEach
    void setUp() {
        when(tokenRepository.countActiveBySubject(
            eq(PersonalAccessTokenSubjectType.ROLE_GROUP), eq(10L), any(LocalDateTime.class)
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
        CreatedPersonalAccessToken result = service.createGroupToken(
            10L,
            session(),
            "财务系统同步",
            "只读角色供给",
            null,
            "127.0.0.1"
        );

        assertThat(result.secret()).isEqualTo("idm_pat_uid_secret");
        assertThat(result.token().getSubjectType()).isEqualTo(PersonalAccessTokenSubjectType.ROLE_GROUP);
        assertThat(result.token().getSubjectId()).isEqualTo(10L);
        assertThat(result.token().getUserId()).isNull();
        assertThat(result.token().getScopeMode()).isEqualTo(PersonalAccessTokenScopeMode.FIXED);
        assertThat(result.token().getPermissions()).isEmpty();
        verify(authorizationService).requireOwner(session(), 10L);

        ArgumentCaptor<PersonalAccessToken> tokenCaptor = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(tokenRepository).create(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getSecretValue()).isEqualTo("idm_pat_uid_secret");
    }

    @Test
    void roleSupplyTokensCannotManageTheirOwnTokenRecords() {
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

        assertThatThrownBy(() -> service.listGroupTokens(10L, groupToken, 1, 20))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void globalTokenManagementRequiresAPlatformAdministratorSession() {
        when(authorizationService.isPlatformAdmin(session())).thenReturn(false);

        assertThatThrownBy(() -> service.listGlobalTokens(session(), 1, 20))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_SUPPLY_TOKEN_FORBIDDEN");
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
