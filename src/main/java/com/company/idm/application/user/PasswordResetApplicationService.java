package com.company.idm.application.user;

import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.user.PasswordGenerator;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.PasswordResetNotificationService;
import com.company.idm.domain.user.PasswordResetThrottleService;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.PasswordResetProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 统一收口管理员重置密码与忘记密码流程。
 */
@Service
@RequiredArgsConstructor
public class PasswordResetApplicationService {

    private static final String DEFAULT_ADMIN_RESET_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final AuditLogRepository auditLogRepository;
    private final PermissionLevelRuleService permissionLevelRuleService;
    private final PasswordGenerator passwordGenerator;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordResetNotificationService passwordResetNotificationService;
    private final PasswordResetThrottleService passwordResetThrottleService;
    private final PasswordResetProperties passwordResetProperties;

    public void adminResetPassword(AdminResetPasswordCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        permissionLevelRuleService.checkCanModifySensitiveUser(command.operator(), user);
        try {
            doAdminReset(user, command.operator(), "USER_PASSWORD_RESET");
        } catch (RuntimeException exception) {
            auditFailure(command.operator(), "USER_PASSWORD_RESET", String.valueOf(user.getId()), exception.getMessage());
            throw exception;
        }
    }

    public void forgotPassword(ForgotPasswordCommand command) {
        String username = normalizeUsername(command.username());
        String clientIp = normalizeClientIp(command.clientIp());
        if (!passwordResetThrottleService.tryAcquire(username, clientIp)) {
            auditLogRepository.save(AuditLog.builder()
                .operator(username)
                .operationType("USER_PASSWORD_FORGOT")
                .bizType("USER")
                .bizId(username)
                .result("THROTTLED")
                .errorMessage("忘记密码请求过于频繁")
                .build());
            return;
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            auditFailure(username, "USER_PASSWORD_FORGOT", username, "用户不存在");
            return;
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            auditFailure(username, "USER_PASSWORD_FORGOT", String.valueOf(user.getId()), "用户已被禁用");
            return;
        }
        if (!hasEmail(user)) {
            auditFailure(username, "USER_PASSWORD_FORGOT", String.valueOf(user.getId()), "用户未配置邮箱");
            return;
        }

        try {
            doForgotPasswordReset(user, user.getUsername(), "USER_PASSWORD_FORGOT");
        } catch (RuntimeException exception) {
            auditFailure(user.getUsername(), "USER_PASSWORD_FORGOT", String.valueOf(user.getId()), exception.getMessage());
        }
    }

    public String forgotPasswordSuccessNotice() {
        return passwordResetProperties.getForgotPasswordSuccessNotice();
    }

    private void doAdminReset(User user, String operator, String operationType) {
        passwordPolicyValidator.validate(DEFAULT_ADMIN_RESET_PASSWORD);
        ldapDirectoryService.resetPassword(user.getUsername(), DEFAULT_ADMIN_RESET_PASSWORD);
        userRepository.bumpTokenVersion(user.getId(), nextTokenVersion(user));
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType(operationType)
            .bizType("USER")
            .bizId(String.valueOf(user.getId()))
            .afterJson(user.getUsername())
            .result("SUCCESS")
            .build());
    }

    private void doForgotPasswordReset(User user, String operator, String operationType) {
        String generatedPassword = passwordGenerator.generateSixDigitNumericPassword();
        passwordPolicyValidator.validate(generatedPassword);
        ldapDirectoryService.resetPassword(user.getUsername(), generatedPassword);
        userRepository.bumpTokenVersion(user.getId(), nextTokenVersion(user));
        passwordResetNotificationService.sendPasswordResetMail(user, generatedPassword);
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType(operationType)
            .bizType("USER")
            .bizId(String.valueOf(user.getId()))
            .afterJson(user.getUsername())
            .result("SUCCESS")
            .build());
    }

    private boolean hasEmail(User user) {
        return user.getEmail() != null && !user.getEmail().isBlank();
    }

    private void auditFailure(String operator, String operationType, String bizId, String message) {
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType(operationType)
            .bizType("USER")
            .bizId(bizId)
            .result("FAIL")
            .errorMessage(message)
            .build());
    }

    private int nextTokenVersion(User user) {
        return user.getTokenVersion() == null ? 1 : user.getTokenVersion() + 1;
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "UNKNOWN";
        }
        return clientIp.trim();
    }
}
