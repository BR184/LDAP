package com.company.idm.application.token;

import com.company.idm.application.rolegroup.RoleGroupAuthorizationService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleSupplyTokenApplicationService {

    private final PersonalAccessTokenRepository tokenRepository;
    private final PersonalAccessTokenSecretService secretService;
    private final PersonalAccessTokenProperties properties;
    private final RoleGroupAuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final PasswordVerificationTokenService passwordVerificationTokenService;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock = Clock.systemDefaultZone();

    public RoleSupplyTokenPage listGroupTokens(
        Long groupId,
        AuthenticatedUser principal,
        int page,
        int pageSize
    ) {
        requireSession(principal);
        authorizationService.requireOwner(principal, groupId);
        return list(PersonalAccessTokenSubjectType.ROLE_GROUP, groupId, page, pageSize);
    }

    public RoleSupplyTokenPage listGlobalTokens(AuthenticatedUser principal, int page, int pageSize) {
        requirePlatformAdminSession(principal);
        return list(PersonalAccessTokenSubjectType.GLOBAL, null, page, pageSize);
    }

    @Transactional
    public CreatedPersonalAccessToken createGroupToken(
        Long groupId,
        AuthenticatedUser principal,
        String name,
        String description,
        LocalDateTime expiresAt,
        String sourceIp
    ) {
        requireSession(principal);
        authorizationService.requireOwner(principal, groupId);
        return create(
            PersonalAccessTokenSubjectType.ROLE_GROUP,
            groupId,
            principal,
            name,
            description,
            expiresAt,
            sourceIp
        );
    }

    @Transactional
    public CreatedPersonalAccessToken createGlobalToken(
        AuthenticatedUser principal,
        String name,
        String description,
        LocalDateTime expiresAt,
        String sourceIp
    ) {
        requirePlatformAdminSession(principal);
        return create(
            PersonalAccessTokenSubjectType.GLOBAL,
            null,
            principal,
            name,
            description,
            expiresAt,
            sourceIp
        );
    }

    @Transactional
    public CreatedPersonalAccessToken rotateGroupToken(
        Long groupId,
        Long tokenId,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        requireSession(principal);
        authorizationService.requireOwner(principal, groupId);
        return rotate(PersonalAccessTokenSubjectType.ROLE_GROUP, groupId, tokenId, principal, sourceIp);
    }

    @Transactional
    public CreatedPersonalAccessToken rotateGlobalToken(
        Long tokenId,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        requirePlatformAdminSession(principal);
        return rotate(PersonalAccessTokenSubjectType.GLOBAL, null, tokenId, principal, sourceIp);
    }

    public String revealGroupToken(
        Long groupId,
        Long tokenId,
        String verificationToken,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        requireSession(principal);
        authorizationService.requireOwner(principal, groupId);
        verifyPasswordToken(principal, verificationToken);
        return reveal(PersonalAccessTokenSubjectType.ROLE_GROUP, groupId, tokenId, principal, sourceIp);
    }

    public String revealGlobalToken(
        Long tokenId,
        String verificationToken,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        requirePlatformAdminSession(principal);
        verifyPasswordToken(principal, verificationToken);
        return reveal(PersonalAccessTokenSubjectType.GLOBAL, null, tokenId, principal, sourceIp);
    }

    @Transactional
    public void revokeGroupToken(
        Long groupId,
        Long tokenId,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        requireSession(principal);
        authorizationService.requireOwner(principal, groupId);
        revoke(PersonalAccessTokenSubjectType.ROLE_GROUP, groupId, tokenId, principal, sourceIp);
    }

    @Transactional
    public void revokeGlobalToken(Long tokenId, AuthenticatedUser principal, String sourceIp) {
        requirePlatformAdminSession(principal);
        revoke(PersonalAccessTokenSubjectType.GLOBAL, null, tokenId, principal, sourceIp);
    }

    @Transactional
    public void deleteGroupToken(
        Long groupId,
        Long tokenId,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        requireSession(principal);
        authorizationService.requireOwner(principal, groupId);
        delete(PersonalAccessTokenSubjectType.ROLE_GROUP, groupId, tokenId, principal, sourceIp);
    }

    @Transactional
    public void deleteGlobalToken(Long tokenId, AuthenticatedUser principal, String sourceIp) {
        requirePlatformAdminSession(principal);
        delete(PersonalAccessTokenSubjectType.GLOBAL, null, tokenId, principal, sourceIp);
    }

    private RoleSupplyTokenPage list(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        int page,
        int pageSize
    ) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        long offset = (long) (safePage - 1) * safePageSize;
        return new RoleSupplyTokenPage(
            tokenRepository.findBySubject(subjectType, subjectId, offset, safePageSize),
            tokenRepository.countBySubject(subjectType, subjectId),
            safePage,
            safePageSize
        );
    }

    private CreatedPersonalAccessToken create(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        AuthenticatedUser principal,
        String name,
        String description,
        LocalDateTime expiresAt,
        String sourceIp
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        validateExpiry(expiresAt, now);
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
            .expiresAt(expiresAt)
            .creator(principal.userId())
            .modifier(principal.userId())
            .gmtCreate(now)
            .gmtModified(now)
            .permissions(java.util.List.of())
            .build());
        audit(principal, sourceIp, "ROLE_SUPPLY_TOKEN_CREATE", persisted.getId(), subjectType, subjectId);
        return new CreatedPersonalAccessToken(persisted, generated.rawToken());
    }

    private CreatedPersonalAccessToken rotate(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        Long tokenId,
        AuthenticatedUser principal,
        String sourceIp
    ) {
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

    private String reveal(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        Long tokenId,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        PersonalAccessToken token = requireToken(tokenId, subjectType, subjectId);
        if (!token.isActiveAt(LocalDateTime.now(clock)) || !token.isSecretRecoverable()) {
            throw new BizException("PAT_SECRET_UNAVAILABLE", "当前访问令牌不可查看");
        }
        audit(principal, sourceIp, "ROLE_SUPPLY_TOKEN_REVEAL", tokenId, subjectType, subjectId);
        return token.getSecretValue();
    }

    private void revoke(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        Long tokenId,
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

    private void delete(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        Long tokenId,
        AuthenticatedUser principal,
        String sourceIp
    ) {
        if (!tokenRepository.delete(tokenId, subjectType, subjectId)) {
            throw new BizException("PAT_NOT_FOUND", "访问令牌不存在");
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

    private void requirePlatformAdminSession(AuthenticatedUser principal) {
        requireSession(principal);
        if (!authorizationService.isPlatformAdmin(principal)) {
            throw new BizException("ROLE_SUPPLY_TOKEN_FORBIDDEN", "只有平台管理员可以管理全局令牌");
        }
    }

    private void requireSession(AuthenticatedUser principal) {
        if (principal == null
            || principal.credentialType() != CredentialType.SESSION
            || principal.id() == null
            || principal.userId() == null) {
            throw new BizException("AUTH_FORBIDDEN", "访问令牌管理仅支持网页登录会话");
        }
    }

    private void validateExpiry(LocalDateTime expiresAt, LocalDateTime now) {
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BizException("PAT_EXPIRY_INVALID", "令牌有效期必须晚于当前时间");
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
