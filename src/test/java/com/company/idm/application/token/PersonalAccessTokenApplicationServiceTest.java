package com.company.idm.application.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.application.user.PasswordVerificationTokenService;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.PermissionType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.PersonalAccessTokenProperties;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PersonalAccessTokenApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-03T10:00:00Z"),
        ZoneId.of("Asia/Shanghai")
    );

    private final PersonalAccessTokenRepository tokenRepository = mock(PersonalAccessTokenRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EffectivePermissionService effectivePermissionService = mock(EffectivePermissionService.class);
    private final PasswordVerificationTokenService passwordVerificationTokenService =
        mock(PasswordVerificationTokenService.class);
    private final PersonalAccessTokenSecretService secretService = mock(PersonalAccessTokenSecretService.class);
    private final PersonalAccessTokenProperties properties = new PersonalAccessTokenProperties();
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final PersonalAccessTokenApplicationService service = new PersonalAccessTokenApplicationService(
        tokenRepository,
        permissionRepository,
        userRepository,
        effectivePermissionService,
        passwordVerificationTokenService,
        secretService,
        properties,
        auditLogRepository,
        CLOCK
    );

    @BeforeEach
    void setUp() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(owner()));
        when(effectivePermissionService.resolve(session())).thenReturn(Set.of("USER_READ"));
        when(permissionRepository.findAll()).thenReturn(List.of(permission(1L, "USER_READ")));
        when(tokenRepository.countActiveByUserId(7L, now())).thenReturn(0L);
        when(secretService.generate()).thenReturn(new GeneratedPersonalAccessTokenSecret(
            "uid",
            "idm_pat_uid_secret",
            "hash",
            1,
            "idm_pat_uid_..."
        ));
        when(tokenRepository.create(any())).thenAnswer(invocation -> invocation.<PersonalAccessToken>getArgument(0)
            .toBuilder()
            .id(11L)
            .build());
    }

    @Test
    void createsAOneTimeSecretForAnOwnedPermissionSubset() {
        CreatedPersonalAccessToken created = service.create(session(), command(List.of(1L), now().plusDays(30)));

        verify(passwordVerificationTokenService).verify(
            org.mockito.ArgumentMatchers.eq("verification"),
            argThat(user -> Long.valueOf(7L).equals(user.getId()) && "employee".equals(user.getUserId()))
        );
        assertThat(created.secret()).isEqualTo("idm_pat_uid_secret");
        assertThat(created.token().getSecretHash()).isEqualTo("hash");
        assertThat(created.token().getPermissions()).extracting(permission -> permission.code())
            .containsExactly("USER_READ");
    }

    @Test
    void rejectsPermissionsOutsideTheOwnersCurrentApiPermissions() {
        assertThatThrownBy(() -> service.create(session(), command(List.of(2L), now().plusDays(30))))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PAT_PERMISSION_SCOPE_INVALID");

        verify(tokenRepository, never()).create(any());
    }

    @Test
    void rejectsPastExpiryAndActiveTokenLimit() {
        assertThatThrownBy(() -> service.create(session(), command(List.of(1L), now())))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("有效期");

        when(tokenRepository.countActiveByUserId(7L, now())).thenReturn(20L);
        assertThatThrownBy(() -> service.create(session(), command(List.of(1L), null)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("上限");
    }

    @Test
    void revokeIsOwnedAndIdempotent() {
        when(tokenRepository.findOwnedById(11L, 7L)).thenReturn(Optional.empty());
        service.revoke(session(), 11L, "127.0.0.1");
        verify(tokenRepository, never()).revokeOwned(any(), any(), any(), any());

        PersonalAccessToken active = PersonalAccessToken.builder().id(11L).userId(7L).build();
        when(tokenRepository.findOwnedById(11L, 7L)).thenReturn(Optional.of(active));
        service.revoke(session(), 11L, "127.0.0.1");
        verify(tokenRepository).revokeOwned(11L, 7L, now(), "employee");
    }

    @Test
    void patCannotManagePersonalAccessTokens() {
        AuthenticatedUser pat = new AuthenticatedUser(
            7L,
            "employee",
            0,
            Set.of(),
            CredentialType.PERSONAL_ACCESS_TOKEN,
            11L,
            Set.of("USER_READ")
        );

        assertThatThrownBy(() -> service.list(pat, 1, 20))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("网页登录会话");
    }

    private CreatePersonalAccessTokenCommand command(List<Long> permissionIds, LocalDateTime expiresAt) {
        return new CreatePersonalAccessTokenCommand(
            "automation",
            expiresAt,
            permissionIds,
            "verification",
            "127.0.0.1"
        );
    }

    private AuthenticatedUser session() {
        return new AuthenticatedUser(7L, "employee", 0, Set.of(), CredentialType.SESSION, null, Set.of());
    }

    private User owner() {
        return User.builder()
            .id(7L)
            .userId("employee")
            .tokenVersion(0)
            .accessAllowed(true)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .build();
    }

    private Permission permission(Long id, String code) {
        return Permission.builder()
            .id(id)
            .permissionCode(code)
            .permissionName(code)
            .permissionType(PermissionType.API)
            .resourcePath("/api")
            .action("GET")
            .status(1)
            .build();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CLOCK);
    }
}
