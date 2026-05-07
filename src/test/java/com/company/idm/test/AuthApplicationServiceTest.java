package com.company.idm.test;

import com.company.idm.application.auth.AuthApplicationService;
import com.company.idm.application.auth.LoginCommand;
import com.company.idm.application.auth.LoginResult;
import com.company.idm.application.auth.TokenService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证认证应用服务核心分支的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LdapDirectoryService ldapDirectoryService;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuthApplicationService authApplicationService;

    @Test
    void shouldLoginSuccessfully() {
        User user = buildUser(UserStatus.ENABLED, 0);
        LoginResult expected = new LoginResult(1L, "admin", Set.of("ADMIN"), "token", Instant.now());

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(ldapDirectoryService.authenticate("admin", "admin123")).thenReturn(true);
        when(userRepository.findRoleCodesByUsername("admin")).thenReturn(Set.of("ADMIN"));
        when(tokenService.generate(org.mockito.ArgumentMatchers.any(User.class), org.mockito.ArgumentMatchers.eq(Set.of("ADMIN"))))
            .thenReturn(expected);

        LoginResult actual = authApplicationService.login(new LoginCommand("admin", "admin123"));

        assertThat(actual).isEqualTo(expected);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().getOperator()).isEqualTo("admin");
    }

    @Test
    void shouldRejectDisabledUserLogin() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(buildUser(UserStatus.DISABLED, 0)));

        assertThatThrownBy(() -> authApplicationService.login(new LoginCommand("admin", "admin123")))
            .isInstanceOf(BizException.class)
            .hasMessage("用户已被禁用");
    }

    @Test
    void shouldRejectInvalidPassword() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(buildUser(UserStatus.ENABLED, 0)));
        when(ldapDirectoryService.authenticate("admin", "bad")).thenReturn(false);

        assertThatThrownBy(() -> authApplicationService.login(new LoginCommand("admin", "bad")))
            .isInstanceOf(BizException.class)
            .hasMessage("用户名或密码错误");
    }

    @Test
    void shouldLoadProfileWithRoleCodes() {
        User user = buildUser(UserStatus.ENABLED, 0);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(userRepository.findRoleCodesByUsername("admin")).thenReturn(Set.of("ADMIN"));

        User profile = authApplicationService.loadProfile("admin");

        assertThat(profile.getRoleCodes()).containsExactly("ADMIN");
    }

    @Test
    void shouldLoginByEmployeeNoForNonAdminUser() {
        User user = buildUser(UserStatus.ENABLED, 0).toBuilder()
            .id(2L)
            .username("zhangsan1001")
            .employeeNo("1001")
            .realName("张三")
            .build();
        LoginResult expected = new LoginResult(2L, "zhangsan1001", Set.of("NORMAL_USER"), "token", Instant.now());

        when(userRepository.findByUsername("1001")).thenReturn(Optional.empty());
        when(userRepository.findByEmployeeNo("1001")).thenReturn(Optional.of(user));
        when(ldapDirectoryService.authenticate("zhangsan1001", "abc123")).thenReturn(true);
        when(userRepository.findRoleCodesByUsername("zhangsan1001")).thenReturn(Set.of("NORMAL_USER"));
        when(tokenService.generate(org.mockito.ArgumentMatchers.any(User.class), org.mockito.ArgumentMatchers.eq(Set.of("NORMAL_USER"))))
            .thenReturn(expected);

        LoginResult actual = authApplicationService.login(new LoginCommand("1001", "abc123"));

        assertThat(actual).isEqualTo(expected);
    }

    private User buildUser(UserStatus status, int tokenVersion) {
        return User.builder()
            .id(1L)
            .username("admin")
            .realName("管理员")
            .email("admin@corp.local")
            .mobile("13800000000")
            .employeeNo("E001")
            .deptCode("D001")
            .status(status)
            .sourceType(SourceType.MANUAL)
            .ldapDn("uid=admin,ou=people,dc=corp,dc=local")
            .tokenVersion(tokenVersion)
            .roleCodes(Set.of())
            .build();
    }
}
