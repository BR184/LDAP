package com.company.idm.application.user;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.PasswordResetProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetApplicationService {

    private static final String DEFAULT_ADMIN_RESET_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final AuditLogRepository auditLogRepository;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordResetProperties passwordResetProperties;
    private final PasswordResetAuthorizationService passwordResetAuthorizationService;

    public void adminResetPassword(AdminResetPasswordCommand command) {
        User target = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        try {
            User operator = userRepository.findByUserId(command.operator())
                .orElseThrow(() -> new BizException("USER_NOT_FOUND", "操作人不存在"));
            List<User> allUsers = userRepository.findAll();
            PasswordResetScope scope = passwordResetAuthorizationService.checkCanReset(
                operator,
                target,
                allUsers,
                command.effectivePermissionCodes()
            );
            doAdminReset(target, command.operator(), command.operatorIp(), scope);
        } catch (RuntimeException exception) {
            auditFailure(command.operator(), command.operatorIp(), String.valueOf(target.getId()), exception.getMessage());
            throw exception;
        }
    }

    public void forgotPassword(ForgotPasswordCommand command) {
        String loginId = normalizeLoginId(command.userId());
        String clientIp = normalizeClientIp(command.clientIp());
        auditLogRepository.save(AuditLog.builder()
            .operator(loginId)
            .operatorIp(clientIp)
            .operationType("USER_PASSWORD_FORGOT")
            .bizType("USER")
            .bizId(loginId)
            .afterJson("{\"notice\":\"CONTACT_MANAGER_OR_ADMIN\"}")
            .result("SUCCESS")
            .build());
    }

    public String forgotPasswordSuccessNotice() {
        return passwordResetProperties.getForgotPasswordSuccessNotice();
    }

    private void doAdminReset(User target, String operator, String operatorIp, PasswordResetScope scope) {
        passwordPolicyValidator.validate(DEFAULT_ADMIN_RESET_PASSWORD);
        ldapDirectoryService.resetPassword(target.getUserId(), DEFAULT_ADMIN_RESET_PASSWORD);
        userRepository.bumpTokenVersion(target.getId(), nextTokenVersion(target));
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operatorIp(operatorIp)
            .operationType("USER_PASSWORD_RESET")
            .bizType("USER")
            .bizId(String.valueOf(target.getId()))
            .afterJson(buildResetAuditJson(target.getUserId(), scope))
            .result("SUCCESS")
            .build());
    }

    private void auditFailure(String operator, String operatorIp, String bizId, String message) {
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operatorIp(operatorIp)
            .operationType("USER_PASSWORD_RESET")
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

    private String buildResetAuditJson(String userId, PasswordResetScope scope) {
        return "{\"userId\":\"" + escapeJson(userId) + "\",\"scope\":\"" + scope.name() + "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
