package com.company.idm.application.token;

import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.common.enums.PermissionType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenPermission;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.domain.token.PersonalAccessTokenScopeMode;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.PersonalAccessTokenProperties;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalAccessTokenApplicationService {

    private final PersonalAccessTokenRepository tokenRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final EffectivePermissionService effectivePermissionService;
    private final PersonalAccessTokenSecretService secretService;
    private final PersonalAccessTokenEncryptionService encryptionService;
    private final PersonalAccessTokenProperties properties;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    @Autowired
    public PersonalAccessTokenApplicationService(
        PersonalAccessTokenRepository tokenRepository,
        PermissionRepository permissionRepository,
        UserRepository userRepository,
        EffectivePermissionService effectivePermissionService,
        PersonalAccessTokenSecretService secretService,
        PersonalAccessTokenEncryptionService encryptionService,
        PersonalAccessTokenProperties properties,
        AuditLogRepository auditLogRepository
    ) {
        this(
            tokenRepository,
            permissionRepository,
            userRepository,
            effectivePermissionService,
            secretService,
            encryptionService,
            properties,
            auditLogRepository,
            Clock.systemDefaultZone()
        );
    }

    PersonalAccessTokenApplicationService(
        PersonalAccessTokenRepository tokenRepository,
        PermissionRepository permissionRepository,
        UserRepository userRepository,
        EffectivePermissionService effectivePermissionService,
        PersonalAccessTokenSecretService secretService,
        PersonalAccessTokenEncryptionService encryptionService,
        PersonalAccessTokenProperties properties,
        AuditLogRepository auditLogRepository,
        Clock clock
    ) {
        this.tokenRepository = tokenRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.effectivePermissionService = effectivePermissionService;
        this.secretService = secretService;
        this.encryptionService = encryptionService;
        this.properties = properties;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    public PersonalAccessTokenPage list(AuthenticatedUser principal, int page, int pageSize) {
        requireSession(principal);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        long offset = (long) (safePage - 1) * safePageSize;
        return new PersonalAccessTokenPage(
            tokenRepository.findByUserId(principal.id(), offset, safePageSize),
            tokenRepository.countByUserId(principal.id()),
            safePage,
            safePageSize
        );
    }

    public List<Permission> availablePermissions(AuthenticatedUser principal) {
        requireSession(principal);
        var effectiveCodes = effectivePermissionService.resolve(principal);
        return permissionRepository.findAll().stream()
            .filter(permission -> permission.getPermissionType() == PermissionType.API)
            .filter(permission -> Integer.valueOf(1).equals(permission.getStatus()))
            .filter(permission -> effectiveCodes.contains(permission.getPermissionCode()))
            .toList();
    }

    @Transactional
    public CreatedPersonalAccessToken create(
        AuthenticatedUser principal,
        CreatePersonalAccessTokenCommand command
    ) {
        requireSession(principal);
        User owner = requireOwner(principal);
        LocalDateTime now = LocalDateTime.now(clock);
        validateExpiry(command.expiresAt(), now);
        if (tokenRepository.countActiveByUserId(principal.id(), now) >= properties.getMaxActivePerUser()) {
            throw new BizException("PAT_ACTIVE_LIMIT_EXCEEDED", "有效个人访问令牌数量已达到上限");
        }

        PersonalAccessTokenScopeMode scopeMode = command.scopeMode() == null
            ? PersonalAccessTokenScopeMode.FIXED
            : command.scopeMode();
        List<Long> selectedIds = scopeMode == PersonalAccessTokenScopeMode.FIXED
            ? normalizePermissionIds(command.permissionIds())
            : normalizeFollowAccountPermissionIds(command.permissionIds());
        Map<Long, Permission> availableById = availablePermissions(principal).stream()
            .collect(Collectors.toMap(Permission::getId, Function.identity()));
        if (!availableById.keySet().containsAll(selectedIds)) {
            throw new BizException("PAT_PERMISSION_SCOPE_INVALID", "令牌权限必须是当前账号API权限的子集");
        }
        List<PersonalAccessTokenPermission> selectedPermissions = selectedIds.stream()
            .map(availableById::get)
            .map(this::toTokenPermission)
            .toList();
        GeneratedPersonalAccessTokenSecret generated = secretService.generate();
        EncryptedPersonalAccessTokenSecret encrypted = encryptionService.encrypt(
            generated.rawToken(),
            principal.id(),
            generated.tokenUid()
        );
        String name = normalizeName(command.name());
        PersonalAccessToken persisted = tokenRepository.create(PersonalAccessToken.builder()
            .tokenUid(generated.tokenUid())
            .userId(principal.id())
            .name(name)
            .description(normalizeDescription(command.description()))
            .scopeMode(scopeMode)
            .secretHash(generated.secretHash())
            .secretCiphertext(encrypted.ciphertext())
            .secretKeyId(encrypted.keyId())
            .hashVersion(generated.hashVersion())
            .tokenPrefix(generated.displayPrefix())
            .expiresAt(command.expiresAt())
            .creator(principal.userId())
            .modifier(principal.userId())
            .gmtCreate(now)
            .gmtModified(now)
            .permissions(selectedPermissions)
            .build());
        auditLogRepository.save(AuditLog.builder()
            .operator(principal.userId())
            .operatorIp(command.sourceIp())
            .operationType("PAT_CREATE")
            .bizType("PERSONAL_ACCESS_TOKEN")
            .bizId(String.valueOf(persisted.getId()))
            .afterJson("{\"permissionCount\":" + selectedPermissions.size()
                + ",\"expiresAt\":\"" + (persisted.getExpiresAt() == null ? "NEVER" : persisted.getExpiresAt()) + "\"}")
            .result("SUCCESS")
            .build());
        return new CreatedPersonalAccessToken(persisted, generated.rawToken());
    }

    @Transactional
    public CreatedPersonalAccessToken rotate(
        AuthenticatedUser principal,
        Long tokenId,
        String sourceIp
    ) {
        requireSession(principal);
        PersonalAccessToken existing = tokenRepository.findOwnedById(tokenId, principal.id())
            .orElseThrow(() -> new BizException("PAT_NOT_FOUND", "个人访问密钥不存在"));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!existing.isActiveAt(now)) {
            throw new BizException("PAT_NOT_ACTIVE", "只有有效的个人访问密钥可以轮换");
        }
        GeneratedPersonalAccessTokenSecret generated = secretService.generate();
        EncryptedPersonalAccessTokenSecret encrypted = encryptionService.encrypt(
            generated.rawToken(),
            principal.id(),
            generated.tokenUid()
        );
        PersonalAccessToken rotated = existing.toBuilder()
            .tokenUid(generated.tokenUid())
            .secretHash(generated.secretHash())
            .secretCiphertext(encrypted.ciphertext())
            .secretKeyId(encrypted.keyId())
            .hashVersion(generated.hashVersion())
            .tokenPrefix(generated.displayPrefix())
            .modifier(principal.userId())
            .gmtModified(now)
            .build();
        if (!tokenRepository.rotateOwned(rotated)) {
            throw new BizException("PAT_ROTATION_CONFLICT", "个人访问密钥状态已变化，请刷新后重试");
        }
        auditLogRepository.save(AuditLog.builder()
            .operator(principal.userId())
            .operatorIp(sourceIp)
            .operationType("PAT_ROTATE")
            .bizType("PERSONAL_ACCESS_TOKEN")
            .bizId(String.valueOf(tokenId))
            .result("SUCCESS")
            .build());
        return new CreatedPersonalAccessToken(rotated, generated.rawToken());
    }

    public String reveal(AuthenticatedUser principal, Long tokenId, String sourceIp) {
        requireSession(principal);
        PersonalAccessToken token = tokenRepository.findOwnedById(tokenId, principal.id())
            .orElseThrow(() -> new BizException("PAT_NOT_FOUND", "个人访问密钥不存在"));
        if (!token.isActiveAt(LocalDateTime.now(clock))) {
            throw new BizException("PAT_NOT_ACTIVE", "只有有效的个人访问密钥可以查看");
        }
        String secret = encryptionService.decrypt(
            token.getSecretCiphertext(),
            token.getSecretKeyId(),
            principal.id(),
            token.getTokenUid()
        );
        auditLogRepository.save(AuditLog.builder()
            .operator(principal.userId())
            .operatorIp(sourceIp)
            .operationType("PAT_REVEAL")
            .bizType("PERSONAL_ACCESS_TOKEN")
            .bizId(String.valueOf(tokenId))
            .result("SUCCESS")
            .build());
        return secret;
    }

    @Transactional
    public void revoke(AuthenticatedUser principal, Long tokenId, String sourceIp) {
        requireSession(principal);
        PersonalAccessToken token = tokenRepository.findOwnedById(tokenId, principal.id()).orElse(null);
        if (token == null || token.getRevokedAt() != null) {
            return;
        }
        LocalDateTime revokedAt = LocalDateTime.now(clock);
        if (tokenRepository.revokeOwned(tokenId, principal.id(), revokedAt, principal.userId())) {
            auditLogRepository.save(AuditLog.builder()
                .operator(principal.userId())
                .operatorIp(sourceIp)
                .operationType("PAT_REVOKE")
                .bizType("PERSONAL_ACCESS_TOKEN")
                .bizId(String.valueOf(tokenId))
                .result("SUCCESS")
                .build());
        }
    }

    @Transactional
    public void delete(AuthenticatedUser principal, Long tokenId, String sourceIp) {
        requireSession(principal);
        if (!tokenRepository.deleteOwned(tokenId, principal.id())) {
            throw new BizException("PAT_NOT_FOUND", "个人访问密钥不存在");
        }
        auditLogRepository.save(AuditLog.builder()
            .operator(principal.userId())
            .operatorIp(sourceIp)
            .operationType("PAT_DELETE")
            .bizType("PERSONAL_ACCESS_TOKEN")
            .bizId(String.valueOf(tokenId))
            .result("SUCCESS")
            .build());
    }

    private User requireOwner(AuthenticatedUser principal) {
        User owner = userRepository.findById(principal.id())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        if (!owner.getUserId().equals(principal.userId())) {
            throw new BizException("AUTH_INVALID", "认证信息无效");
        }
        return owner;
    }

    private void requireSession(AuthenticatedUser principal) {
        if (principal == null || principal.credentialType() != CredentialType.SESSION || principal.id() == null) {
            throw new BizException("AUTH_FORBIDDEN", "个人访问令牌管理仅支持网页登录会话");
        }
    }

    private void validateExpiry(LocalDateTime expiresAt, LocalDateTime now) {
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BizException("PAT_EXPIRY_INVALID", "令牌有效期必须晚于当前时间");
        }
    }

    private List<Long> normalizePermissionIds(List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            throw new BizException("PAT_PERMISSION_REQUIRED", "请至少选择一项API权限");
        }
        LinkedHashSet<Long> normalized = permissionIds.stream()
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            throw new BizException("PAT_PERMISSION_REQUIRED", "请至少选择一项API权限");
        }
        return List.copyOf(normalized);
    }

    private List<Long> normalizeFollowAccountPermissionIds(List<Long> permissionIds) {
        if (permissionIds != null && !permissionIds.isEmpty()) {
            throw new BizException("PAT_PERMISSION_SCOPE_INVALID", "自动跟随账号权限的令牌不接受固定权限范围");
        }
        return List.of();
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

    private PersonalAccessTokenPermission toTokenPermission(Permission permission) {
        return new PersonalAccessTokenPermission(
            permission.getId(),
            permission.getPermissionCode(),
            permission.getPermissionName(),
            permission.getResourcePath(),
            permission.getAction()
        );
    }
}
