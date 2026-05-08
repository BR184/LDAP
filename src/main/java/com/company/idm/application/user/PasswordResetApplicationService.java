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
    private final IntranetEmailGenerationService intranetEmailGenerationService;

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
        String loginId = normalizeLoginId(command.userId());
        String clientIp = normalizeClientIp(command.clientIp());
        if (!passwordResetThrottleService.tryAcquire(loginId, clientIp)) {
            auditLogRepository.save(AuditLog.builder()
                .operator(loginId)
                .operationType("USER_PASSWORD_FORGOT")
                .bizType("USER")
                .bizId(loginId)
                .result("THROTTLED")
                .errorMessage("忘记密码请求过于频繁")
                .build());
            return;
        }

        User user = resolveUserForPasswordReset(loginId);
        if (user == null) {
            auditFailure(loginId, "USER_PASSWORD_FORGOT", loginId, "用户不存在");
            return;
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            auditFailure(loginId, "USER_PASSWORD_FORGOT", String.valueOf(user.getId()), "用户已被禁用");
            return;
        }
        user = ensureIntranetEmail(user);
        if (!hasIntranetEmail(user)) {
            auditFailure(loginId, "USER_PASSWORD_FORGOT", String.valueOf(user.getId()), "用户未配置内网邮箱");
            return;
        }

        try {
            doForgotPasswordReset(user, user.getUserId(), "USER_PASSWORD_FORGOT");
        } catch (RuntimeException exception) {
            auditFailure(user.getUserId(), "USER_PASSWORD_FORGOT", String.valueOf(user.getId()), exception.getMessage());
        }
    }

    public String forgotPasswordSuccessNotice() {
        return passwordResetProperties.getForgotPasswordSuccessNotice();
    }

    private void doAdminReset(User user, String operator, String operationType) {
        passwordPolicyValidator.validate(DEFAULT_ADMIN_RESET_PASSWORD);
        ldapDirectoryService.resetPassword(user.getUserId(), DEFAULT_ADMIN_RESET_PASSWORD);
        userRepository.bumpTokenVersion(user.getId(), nextTokenVersion(user));
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType(operationType)
            .bizType("USER")
            .bizId(String.valueOf(user.getId()))
            .afterJson(user.getUserId())
            .result("SUCCESS")
            .build());
    }

    private void doForgotPasswordReset(User user, String operator, String operationType) {
        String generatedPassword = passwordGenerator.generateSixDigitNumericPassword();
        passwordPolicyValidator.validate(generatedPassword);
        ldapDirectoryService.resetPassword(user.getUserId(), generatedPassword);
        userRepository.bumpTokenVersion(user.getId(), nextTokenVersion(user));
        passwordResetNotificationService.sendPasswordResetMail(user, generatedPassword);
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType(operationType)
            .bizType("USER")
            .bizId(String.valueOf(user.getId()))
            .afterJson(user.getUserId())
            .result("SUCCESS")
            .build());
    }

    private boolean hasIntranetEmail(User user) {
        return user.getIntranetEmail() != null && !user.getIntranetEmail().isBlank();
    }

    private User ensureIntranetEmail(User user) {
        if (user == null || hasIntranetEmail(user)) {
            return user;
        }
        String uniqueIdentifier = user.getUserId() != null && !user.getUserId().isBlank()
            ? user.getUserId()
            : String.valueOf(user.getId());
        String intranetEmail = intranetEmailGenerationService.generate(uniqueIdentifier, user.getId());
        return userRepository.save(user.toBuilder().intranetEmail(intranetEmail).build());
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

    private String normalizeLoginId(String loginId) {
        return loginId == null ? "" : loginId.trim();
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "UNKNOWN";
        }
        return clientIp.trim();
    }

    private User resolveUserForPasswordReset(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return null;
        }
        User byUserId = userRepository.findByUserId(loginId).orElse(null);
        if (byUserId != null) {
            return byUserId;
        }
        return userRepository.findByEmployeeNo(loginId).orElse(null);
    }
}
