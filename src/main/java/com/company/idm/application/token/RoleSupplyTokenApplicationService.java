package com.company.idm.application.token;

import com.company.idm.application.user.PasswordVerificationTokenService;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.domain.token.PersonalAccessTokenScopeMode;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.PersonalAccessTokenProperties;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订阅令牌的内部能力：由角色变更推送订阅自动持有与维护，不再对外暴露管理入口。
 * 令牌主体沿用 ROLE_GROUP/GLOBAL，开放供给接口的鉴权链路保持不变。
 */
@Service
@RequiredArgsConstructor
public class RoleSupplyTokenApplicationService {

    private final PersonalAccessTokenRepository tokenRepository;
    private final PersonalAccessTokenSecretService secretService;
    private final PersonalAccessTokenProperties properties;
    private final UserRepository userRepository;
    private final PasswordVerificationTokenService passwordVerificationTokenService;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public CreatedPersonalAccessToken createTokenFor(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        String name,
        String description,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        requireSession(principal);
        LocalDateTime now = LocalDateTime.now(clock);
        if (tokenRepository.countActiveBySubject(subjectType, subjectId, now)
            >= properties.getMaxActivePerUser()) {
            throw new BizException("PAT_ACTIVE_LIMIT_EXCEEDED", "有效访问令牌数量已达到上限");
        }
        GeneratedPersonalAccessTokenSecret generated = secretService.generate();
        PersonalAccessToken persisted = tokenRepository.create(PersonalAccessToken.builder()
            .tokenUid(generated.tokenUid())
            .userId(null)
            .subjectType(subjectType)
            .subjectId(subjectId)
            .name(normalizeName(name))
            .description(normalizeDescription(description))
            .scopeMode(PersonalAccessTokenScopeMode.FIXED)
            .secretHash(generated.secretHash())
            .secretValue(generated.rawToken())
            .hashVersion(generated.hashVersion())
            .tokenPrefix(generated.displayPrefix())
            .expiresAt(null)
            .creator(principal.userId())
            .modifier(principal.userId())
            .gmtCreate(now)
            .gmtModified(now)
            .permissions(List.of())
            .build());
        audit(principal, sourceIp, "ROLE_SUPPLY_TOKEN_CREATE", persisted.getId(), subjectType, subjectId);
        return new CreatedPersonalAccessToken(persisted, generated.rawToken());
    }

    @Transactional
    public CreatedPersonalAccessToken rotateToken(
        Long tokenId,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        requireSession(principal);
        PersonalAccessToken existing = requireToken(tokenId, subjectType, subjectId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!existing.isActiveAt(now)) {
            throw new BizException("PAT_NOT_ACTIVE", "只有有效的访问令牌可以轮换");
        }
        GeneratedPersonalAccessTokenSecret generated = secretService.generate();
        PersonalAccessToken rotated = existing.toBuilder()
            .tokenUid(generated.tokenUid())
            .secretHash(generated.secretHash())
            .secretValue(generated.rawToken())
            .hashVersion(generated.hashVersion())
            .tokenPrefix(generated.displayPrefix())
            .modifier(principal.userId())
            .gmtModified(now)
            .build();
        if (!tokenRepository.rotate(rotated)) {
            throw new BizException("PAT_ROTATION_CONFLICT", "访问令牌状态已变化，请刷新后重试");
        }
        audit(principal, sourceIp, "ROLE_SUPPLY_TOKEN_ROTATE", tokenId, subjectType, subjectId);
        return new CreatedPersonalAccessToken(rotated, generated.rawToken());
    }

    public String revealToken(
        Long tokenId,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        String verificationToken,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        requireSession(principal);
        verifyPasswordToken(principal, verificationToken);
        PersonalAccessToken token = requireToken(tokenId, subjectType, subjectId);
        if (!token.isActiveAt(LocalDateTime.now(clock)) || !token.isSecretRecoverable()) {
            throw new BizException("PAT_SECRET_UNAVAILABLE", "当前访问令牌不可查看");
        }
        audit(principal, sourceIp, "ROLE_SUPPLY_TOKEN_REVEAL", tokenId, subjectType, subjectId);
        return token.getSecretValue();
    }

    @Transactional
    public void revokeToken(
        Long tokenId,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        PersonalAccessToken token = tokenRepository.findByIdAndSubject(tokenId, subjectType, subjectId).orElse(null);
        if (token == null || token.getRevokedAt() != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (tokenRepository.revoke(tokenId, subjectType, subjectId, now, principal.userId())) {
            audit(principal, sourceIp, "ROLE_SUPPLY_TOKEN_REVOKE", tokenId, subjectType, subjectId);
        }
    }

    /**
     * 删除令牌；令牌已不存在时按幂等处理，便于订阅删除流程重试。
     */
    @Transactional
    public void deleteToken(
        Long tokenId,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        if (!tokenRepository.delete(tokenId, subjectType, subjectId)) {
            return;
        }
        audit(principal, sourceIp, "ROLE_SUPPLY_TOKEN_DELETE", tokenId, subjectType, subjectId);
    }

    private PersonalAccessToken requireToken(
        Long tokenId,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId
    ) {
        return tokenRepository.findByIdAndSubject(tokenId, subjectType, subjectId)
            .orElseThrow(() -> new BizException("PAT_NOT_FOUND", "访问令牌不存在"));
    }

    private void verifyPasswordToken(AuthenticatedUser principal, String verificationToken) {
        User user = userRepository.findById(principal.id())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        passwordVerificationTokenService.verify(verificationToken, user);
    }

    private void requireSession(AuthenticatedUser principal) {
        if (principal == null
            || principal.credentialType() != CredentialType.SESSION
            || principal.id() == null
            || principal.userId() == null) {
            throw new BizException("AUTH_FORBIDDEN", "访问令牌管理仅支持网页登录会话");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException("PAT_NAME_REQUIRED", "令牌名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.length() > 64) {
            throw new BizException("PAT_NAME_TOO_LONG", "令牌名称不能超过64个字符");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.length() > 255) {
            throw new BizException("PAT_DESCRIPTION_TOO_LONG", "令牌描述不能超过255个字符");
        }
        return normalized;
    }

    private void audit(
        AuthenticatedUser principal,
        String sourceIp,
        String operationType,
        Long tokenId,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId
    ) {
        auditLogRepository.save(AuditLog.builder()
            .operator(principal.userId())
            .operatorIp(sourceIp)
            .operationType(operationType)
            .bizType("PERSONAL_ACCESS_TOKEN")
            .bizId(String.valueOf(tokenId))
            .afterJson("{\"subjectType\":\"" + subjectType.name() + "\",\"subjectId\":"
                + (subjectId == null ? "null" : subjectId) + "}")
            .result("SUCCESS")
            .build());
    }
}
