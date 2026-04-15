package com.company.idm.application.auth;

import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证应用服务，负责协调登录认证、令牌签发与用户资料加载。
 */
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final TokenService tokenService;
    private final AuditLogRepository auditLogRepository;

    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByUsername(command.username())
            .orElseThrow(() -> failure("AUTH_INVALID", "用户名或密码错误", command.username()));
        if (user.getStatus() != UserStatus.ENABLED) {
            throw failure("AUTH_DISABLED", "用户已被禁用", command.username());
        }
        if (!ldapDirectoryService.authenticate(command.username(), command.password())) {
            throw failure("AUTH_INVALID", "用户名或密码错误", command.username());
        }
        Set<String> roleCodes = userRepository.findRoleCodesByUsername(command.username());
        auditLogRepository.save(AuditLog.builder()
            .operator(command.username())
            .operationType("LOGIN")
            .bizType("AUTH")
            .bizId(String.valueOf(user.getId()))
            .result("SUCCESS")
            .build());
        return tokenService.generate(user.toBuilder().roleCodes(roleCodes).build(), roleCodes);
    }

    public User loadProfile(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        return user.toBuilder().roleCodes(userRepository.findRoleCodesByUsername(username)).build();
    }

    private BizException failure(String code, String message, String operator) {
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("LOGIN")
            .bizType("AUTH")
            .result("FAIL")
            .errorMessage(message)
            .build());
        return new BizException(code, message);
    }
}

