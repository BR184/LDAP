package com.company.idm.test;

import com.company.idm.application.user.AdminResetPasswordCommand;
import com.company.idm.application.user.ForgotPasswordCommand;
import com.company.idm.application.user.IntranetEmailGenerationService;
import com.company.idm.application.user.PasswordResetApplicationService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.user.PasswordGenerator;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.PasswordResetNotificationService;
import com.company.idm.domain.user.PasswordResetThrottleService;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LdapDirectoryService ldapDirectoryService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PermissionLevelRuleService permissionLevelRuleService;

    @Mock
    private PasswordGenerator passwordGenerator;

    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;

    @Mock
    private PasswordResetNotificationService passwordResetNotificationService;

    @Mock
    private PasswordResetThrottleService passwordResetThrottleService;

    @Mock
    private IntranetEmailGenerationService intranetEmailGenerationService;

    @InjectMocks
    private PasswordResetApplicationService passwordResetApplicationService;

    @Test
    void shouldResetPasswordToDefaultPasswordWhenAdminResetsUser() {
        User user = buildUser("zhangsan", "zhangsan@corp.local", UserStatus.ENABLED, 0);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        passwordResetApplicationService.adminResetPassword(new AdminResetPasswordCommand(2L, "admin"));

        verify(passwordPolicyValidator).validate("123456");
        verify(ldapDirectoryService).resetPassword("zhangsan", "123456");
        verify(userRepository).bumpTokenVersion(2L, 1);
        verify(passwordResetNotificationService, never()).sendPasswordResetMail(any(), any());
        verify(auditLogRepository).save(argThat(log ->
            "USER_PASSWORD_RESET".equals(log.getOperationType()) && "SUCCESS".equals(log.getResult())
        ));
    }

    @Test
    void shouldGenerateRandomPasswordAndSendMailWhenUserForgetsPassword() {
        User user = buildUser("zhangsan", "zhangsan@corp.local", UserStatus.ENABLED, 2);
        when(passwordResetThrottleService.tryAcquire("zhangsan", "127.0.0.1")).thenReturn(true);
        when(userRepository.findByUsername("zhangsan")).thenReturn(Optional.of(user));
        when(passwordGenerator.generateSixDigitNumericPassword()).thenReturn("654321");

        passwordResetApplicationService.forgotPassword(new ForgotPasswordCommand("zhangsan", "127.0.0.1"));

        verify(passwordPolicyValidator).validate("654321");
        verify(ldapDirectoryService).resetPassword("zhangsan", "654321");
        verify(userRepository).bumpTokenVersion(2L, 3);
        verify(passwordResetNotificationService).sendPasswordResetMail(user, "654321");
        verify(auditLogRepository).save(argThat(log ->
            "USER_PASSWORD_FORGOT".equals(log.getOperationType()) && "SUCCESS".equals(log.getResult())
        ));
    }

    @Test
    void shouldResolveForgotPasswordByEmployeeNo() {
        User user = buildUser("zhangsan1001", "zhangsan@corp.local", UserStatus.ENABLED, 2).toBuilder()
            .employeeNo("1001")
            .build();
        when(passwordResetThrottleService.tryAcquire("1001", "127.0.0.1")).thenReturn(true);
        when(userRepository.findByUsername("1001")).thenReturn(Optional.empty());
        when(userRepository.findByEmployeeNo("1001")).thenReturn(Optional.of(user));
        when(passwordGenerator.generateSixDigitNumericPassword()).thenReturn("654321");

        passwordResetApplicationService.forgotPassword(new ForgotPasswordCommand("1001", "127.0.0.1"));

        verify(ldapDirectoryService).resetPassword("zhangsan1001", "654321");
        verify(passwordResetNotificationService).sendPasswordResetMail(user, "654321");
    }

    @Test
    void shouldIgnoreForgotPasswordWhenUserNotFound() {
        when(passwordResetThrottleService.tryAcquire("ghost", "127.0.0.1")).thenReturn(true);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatCode(() -> passwordResetApplicationService.forgotPassword(new ForgotPasswordCommand("ghost", "127.0.0.1")))
            .doesNotThrowAnyException();

        verify(ldapDirectoryService, never()).resetPassword(any(), any());
        verify(passwordResetNotificationService, never()).sendPasswordResetMail(any(), any());
    }

    @Test
    void shouldGenerateIntranetEmailWhenForgotPasswordUserHasNoWorkEmail() {
        User user = buildUser("zhangsan", null, UserStatus.ENABLED, 0);
        when(passwordResetThrottleService.tryAcquire("zhangsan", "127.0.0.1")).thenReturn(true);
        when(userRepository.findByUsername("zhangsan")).thenReturn(Optional.of(user));
        when(intranetEmailGenerationService.generate("2", 2L)).thenReturn("2@crowncad.com");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class));
        when(passwordGenerator.generateSixDigitNumericPassword()).thenReturn("654321");

        assertThatCode(() -> passwordResetApplicationService.forgotPassword(new ForgotPasswordCommand("zhangsan", "127.0.0.1")))
            .doesNotThrowAnyException();

        verify(ldapDirectoryService).resetPassword("zhangsan", "654321");
        verify(passwordResetNotificationService).sendPasswordResetMail(argThat(savedUser ->
            "2@crowncad.com".equals(savedUser.getIntranetEmail())
        ), org.mockito.ArgumentMatchers.eq("654321"));
    }

    @Test
    void shouldIgnoreForgotPasswordWhenUserDisabled() {
        User user = buildUser("zhangsan", "zhangsan@corp.local", UserStatus.DISABLED, 0);
        when(passwordResetThrottleService.tryAcquire("zhangsan", "127.0.0.1")).thenReturn(true);
        when(userRepository.findByUsername("zhangsan")).thenReturn(Optional.of(user));

        assertThatCode(() -> passwordResetApplicationService.forgotPassword(new ForgotPasswordCommand("zhangsan", "127.0.0.1")))
            .doesNotThrowAnyException();

        verify(ldapDirectoryService, never()).resetPassword(any(), any());
        verify(passwordResetNotificationService, never()).sendPasswordResetMail(any(), any());
    }

    @Test
    void shouldNotResetPasswordWhenForgotPasswordRateLimited() {
        when(passwordResetThrottleService.tryAcquire("zhangsan", "127.0.0.1")).thenReturn(false);

        assertThatCode(() -> passwordResetApplicationService.forgotPassword(new ForgotPasswordCommand("zhangsan", "127.0.0.1")))
            .doesNotThrowAnyException();

        verify(userRepository, never()).findByUsername(any());
        verify(ldapDirectoryService, never()).resetPassword(any(), any());
        verify(passwordResetNotificationService, never()).sendPasswordResetMail(any(), any());
    }

    @Test
    void shouldResetPasswordToDefaultPasswordWhenAdminResetUserHasNoEmail() {
        User user = buildUser("zhangsan", null, UserStatus.ENABLED, 0);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        assertThatCode(() -> passwordResetApplicationService.adminResetPassword(new AdminResetPasswordCommand(2L, "admin")))
            .doesNotThrowAnyException();

        verify(ldapDirectoryService).resetPassword("zhangsan", "123456");
        verify(passwordResetNotificationService, never()).sendPasswordResetMail(any(), any());
    }

    private User buildUser(String username, String email, UserStatus status, int tokenVersion) {
        return User.builder()
            .id(2L)
            .username(username)
            .realName("张三")
            .email(email)
            .intranetEmail(email == null ? null : "e10001@crowncad.com")
            .mobile("13900000000")
            .employeeNo("E10001")
            .deptCode("D001")
            .status(status)
            .sourceType(SourceType.MANUAL)
            .ldapDn("uid=" + username + ",ou=people,dc=corp,dc=local")
            .tokenVersion(tokenVersion)
            .roleCodes(Set.of("ADMIN"))
            .build();
    }
}
