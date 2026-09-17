package com.company.idm.application.rolegroup;

import com.company.idm.application.token.CreatedPersonalAccessToken;
import com.company.idm.application.token.RoleSupplyTokenApplicationService;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rolegroup.PushSubscription;
import com.company.idm.domain.rolegroup.PushSubscriptionRepository;
import com.company.idm.domain.rolegroup.PushSubscriptionStatus;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.config.RabbitmqProperties;
import com.company.idm.infrastructure.rabbitmq.RabbitManagementClient;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 角色变更推送订阅：第三方在平台建立订阅（选定角色集合）后，平台经 RabbitMQ 主动推送成员变更。
 * 订阅自动持有订阅令牌（用于 snapshot/changes 接口）与专属队列账号（用于消费推送），
 * 资源编排通过 RabbitMQ 管理 API 自动完成，无需人工操作 MQ 控制台。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionApplicationService {

    private static final String QUEUE_PREFIX = "idm.sub.";
    private static final String USERNAME_PREFIX = "idm-sub-";
    private static final String ROUTING_KEY_PREFIX = "role.";
    private static final String DEAD_LETTER_EXCHANGE_ARGUMENT = "x-dead-letter-exchange";
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final int MQ_PASSWORD_BYTES = 24;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PushSubscriptionRepository subscriptionRepository;
    private final RoleRepository roleRepository;
    private final RoleGroupAuthorizationService authorizationService;
    private final RoleSupplyScopePolicy scopePolicy;
    private final RoleSupplyTokenApplicationService tokenService;
    private final RabbitManagementClient rabbitManagementClient;
    private final RabbitmqProperties rabbitmqProperties;
    private final AuditLogRepository auditLogRepository;

    public List<SubscriptionView> listGroupSubscriptions(Long groupId, AuthenticatedUser principal) {
        requireSession(principal);
        authorizationService.requireOwner(principal, groupId);
        return toViews(subscriptionRepository.findBySubject(PersonalAccessTokenSubjectType.ROLE_GROUP, groupId));
    }

    public List<SubscriptionView> listGlobalSubscriptions(AuthenticatedUser principal) {
        requireSession(principal);
        requirePlatformAdmin(principal);
        return toViews(subscriptionRepository.findBySubject(PersonalAccessTokenSubjectType.GLOBAL, null));
    }

    @Transactional
    public CreatedSubscription createGroupSubscription(
        Long groupId,
        String name,
        String description,
        List<Long> roleIds,
        AuthenticatedUser principal,
        String sourceIp,
        String requestHost
    ) {
        requireSession(principal);
        authorizationService.requireOwner(principal, groupId);
        return create(
            PersonalAccessTokenSubjectType.ROLE_GROUP,
            groupId,
            name,
            description,
            roleIds,
            principal,
            sourceIp,
            requestHost
        );
    }

    @Transactional
    public CreatedSubscription createGlobalSubscription(
        String name,
        String description,
        List<Long> roleIds,
        AuthenticatedUser principal,
        String sourceIp,
        String requestHost
    ) {
        requireSession(principal);
        requirePlatformAdmin(principal);
        return create(
            PersonalAccessTokenSubjectType.GLOBAL,
            null,
            name,
            description,
            roleIds,
            principal,
            sourceIp,
            requestHost
        );
    }

    @Transactional
    public SubscriptionCredential rotateSubscription(
        Long subscriptionId,
        AuthenticatedUser principal,
        String sourceIp,
        String requestHost
    ) {
        PushSubscription subscription = requireManageableSubscription(subscriptionId, principal);
        CreatedPersonalAccessToken rotated = tokenService.rotateToken(
            subscription.getAccessTokenId(),
            subscription.getSubjectType(),
            subscription.getSubjectId(),
            principal,
            sourceIp
        );
        String password = generateMqPassword();
        // 先在 MQ 侧轮换密码，事务回滚时恢复原密码，保证数据库与 MQ 账号凭证一致。
        registerOnRollback(() -> restoreMqPasswordQuietly(subscription.getMqUsername(), subscription.getMqPassword()));
        rabbitManagementClient.createUser(subscription.getMqUsername(), password);
        if (!subscriptionRepository.updateMqPassword(
            subscriptionId, password, principal.userId(), LocalDateTime.now())) {
            throw new BizException("PUSH_SUBSCRIPTION_CONFLICT", "订阅状态已变化，请刷新后重试");
        }
        audit(principal, sourceIp, "PUSH_SUBSCRIPTION_ROTATE", subscriptionId, subscription.getName());
        return new SubscriptionCredential(
            subscriptionId,
            rotated.secret(),
            mqInfo(subscription.getMqQueue(), subscription.getMqUsername(), password, requestHost)
        );
    }

    public SubscriptionCredential revealSubscription(
        Long subscriptionId,
        String verificationToken,
        AuthenticatedUser principal,
        String sourceIp,
        String requestHost
    ) {
        PushSubscription subscription = requireManageableSubscription(subscriptionId, principal);
        String secret = tokenService.revealToken(
            subscription.getAccessTokenId(),
            subscription.getSubjectType(),
            subscription.getSubjectId(),
            verificationToken,
            principal,
            sourceIp
        );
        return new SubscriptionCredential(
            subscriptionId,
            secret,
            mqInfo(subscription.getMqQueue(), subscription.getMqUsername(), subscription.getMqPassword(), requestHost)
        );
    }

    @Transactional
    public void disableSubscription(Long subscriptionId, AuthenticatedUser principal, String sourceIp) {
        PushSubscription subscription = requireManageableSubscription(subscriptionId, principal);
        if (subscription.getStatus() == PushSubscriptionStatus.DISABLED) {
            return;
        }
        // 先收回 MQ 读取权限，事务回滚时恢复原权限，保证订阅状态与消费者实际能力一致。
        registerOnRollback(() -> restorePermissionQuietly(
            subscription.getMqUsername(), readPattern(subscription.getMqQueue())
        ));
        rabbitManagementClient.grantPermissions(subscription.getMqUsername(), "");
        if (!subscriptionRepository.updateStatus(
            subscriptionId, PushSubscriptionStatus.DISABLED, principal.userId(), LocalDateTime.now())) {
            throw new BizException("PUSH_SUBSCRIPTION_CONFLICT", "订阅状态已变化，请刷新后重试");
        }
        audit(principal, sourceIp, "PUSH_SUBSCRIPTION_DISABLE", subscriptionId, subscription.getName());
    }

    @Transactional
    public void enableSubscription(Long subscriptionId, AuthenticatedUser principal, String sourceIp) {
        PushSubscription subscription = requireManageableSubscription(subscriptionId, principal);
        if (subscription.getStatus() == PushSubscriptionStatus.ENABLED) {
            return;
        }
        // 先授予 MQ 读取权限，事务回滚时收回，保证订阅状态与消费者实际能力一致。
        registerOnRollback(() -> restorePermissionQuietly(subscription.getMqUsername(), ""));
        rabbitManagementClient.grantPermissions(subscription.getMqUsername(), readPattern(subscription.getMqQueue()));
        if (!subscriptionRepository.updateStatus(
            subscriptionId, PushSubscriptionStatus.ENABLED, principal.userId(), LocalDateTime.now())) {
            throw new BizException("PUSH_SUBSCRIPTION_CONFLICT", "订阅状态已变化，请刷新后重试");
        }
        audit(principal, sourceIp, "PUSH_SUBSCRIPTION_ENABLE", subscriptionId, subscription.getName());
    }

    @Transactional
    public void deleteSubscription(Long subscriptionId, AuthenticatedUser principal, String sourceIp) {
        PushSubscription subscription = requireManageableSubscription(subscriptionId, principal);
        if (subscription.getAccessTokenId() != null) {
            tokenService.deleteToken(
                subscription.getAccessTokenId(),
                subscription.getSubjectType(),
                subscription.getSubjectId(),
                principal,
                sourceIp
            );
        }
        subscriptionRepository.delete(subscriptionId);
        audit(principal, sourceIp, "PUSH_SUBSCRIPTION_DELETE", subscriptionId, subscription.getName());
        // 队列与账号属不可恢复资源，改为数据库删除提交后再清理；清理失败仅告警并提示人工核对。
        registerAfterCommit(() -> cleanupDeletedMqResourcesQuietly(subscription));
    }

    private CreatedSubscription create(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        String name,
        String description,
        List<Long> roleIds,
        AuthenticatedUser principal,
        String sourceIp,
        String requestHost
    ) {
        String normalizedName = normalizeRequired(name, "PUSH_SUBSCRIPTION_NAME_REQUIRED", "订阅名称不能为空");
        validateLength(normalizedName, MAX_NAME_LENGTH, "PUSH_SUBSCRIPTION_NAME_TOO_LONG", "订阅名称不能超过64个字符");
        String normalizedDescription = normalizeOptional(description);
        validateLength(
            normalizedDescription,
            MAX_DESCRIPTION_LENGTH,
            "PUSH_SUBSCRIPTION_DESCRIPTION_TOO_LONG",
            "订阅说明不能超过255个字符"
        );
        List<Role> roles = requireSubscribableRoles(subjectType, subjectId, roleIds);
        LocalDateTime now = LocalDateTime.now();
        PushSubscription saved = subscriptionRepository.save(PushSubscription.builder()
            .name(normalizedName)
            .description(normalizedDescription)
            .subjectType(subjectType)
            .subjectId(subjectId)
            .status(PushSubscriptionStatus.ENABLED)
            .creator(principal.userId())
            .modifier(principal.userId())
            .gmtCreate(now)
            .gmtModified(now)
            .build());
        String queueName = QUEUE_PREFIX + saved.getId();
        String username = USERNAME_PREFIX + saved.getId();
        String password = generateMqPassword();
        // 开通流程包含外部资源编排：事务一旦回滚（含审计失败等提交前异常），补偿清理已创建的 MQ 队列与账号。
        registerOnRollback(() -> cleanupQuietly(queueName, username));
        CreatedPersonalAccessToken token = tokenService.createTokenFor(
            subjectType,
            subjectId,
            normalizedName,
            "角色变更推送订阅自动生成的订阅令牌",
            principal,
            sourceIp
        );
        subscriptionRepository.replaceRoles(saved.getId(), roles.stream().map(Role::getId).toList());
        provisionMqResources(queueName, username, password, roles);
        if (!subscriptionRepository.updateProvisioned(
            saved.getId(),
            token.token().getId(),
            queueName,
            username,
            password,
            principal.userId(),
            LocalDateTime.now()
        )) {
            throw new BizException("PUSH_SUBSCRIPTION_PROVISION_FAILED", "订阅开通失败，请稍后重试");
        }
        String tokenSecret = token.secret();
        audit(principal, sourceIp, "PUSH_SUBSCRIPTION_CREATE", saved.getId(), normalizedName);
        PushSubscription provisioned = subscriptionRepository.findById(saved.getId()).orElse(saved);
        return new CreatedSubscription(
            toView(provisioned, roles.size()),
            new SubscriptionCredential(
                saved.getId(),
                tokenSecret,
                mqInfo(queueName, username, password, requestHost)
            )
        );
    }

    private void provisionMqResources(String queueName, String username, String password, List<Role> roles) {
        rabbitManagementClient.ensureTopicExchange(rabbitmqProperties.getExchange());
        rabbitManagementClient.ensureFanoutExchange(rabbitmqProperties.getDeadLetterExchange());
        rabbitManagementClient.ensureQueue(rabbitmqProperties.getDeadLetterQueue(), null);
        rabbitManagementClient.bindQueue(
            rabbitmqProperties.getDeadLetterQueue(),
            rabbitmqProperties.getDeadLetterExchange(),
            ""
        );
        rabbitManagementClient.ensureQueue(
            queueName,
            Map.of(DEAD_LETTER_EXCHANGE_ARGUMENT, rabbitmqProperties.getDeadLetterExchange())
        );
        for (Role role : roles) {
            rabbitManagementClient.bindQueue(
                queueName,
                rabbitmqProperties.getExchange(),
                ROUTING_KEY_PREFIX + role.getRoleCode()
            );
        }
        rabbitManagementClient.createUser(username, password);
        rabbitManagementClient.grantPermissions(username, readPattern(queueName));
    }

    private void cleanupQuietly(String queueName, String username) {
        try {
            rabbitManagementClient.deleteQueue(queueName);
        } catch (RuntimeException exception) {
            log.warn("订阅开通失败后清理队列未完成：queue={}", queueName, exception);
        }
        try {
            rabbitManagementClient.deleteUser(username);
        } catch (RuntimeException exception) {
            log.warn("订阅开通失败后清理账号未完成：username={}", username, exception);
        }
    }

    /** 注册事务回滚后的补偿动作；无事务或提交成功时不执行。 */
    private void registerOnRollback(Runnable compensation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        compensation.run();
                    }
                }
            });
        }
    }

    /** 注册事务提交后的动作；无事务时立即执行。 */
    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void restoreMqPasswordQuietly(String username, String previousPassword) {
        try {
            rabbitManagementClient.createUser(username, previousPassword);
        } catch (RuntimeException exception) {
            log.warn("订阅凭证轮换回滚后恢复MQ密码未完成，请人工核对：username={}", username, exception);
        }
    }

    private void restorePermissionQuietly(String username, String readPattern) {
        try {
            rabbitManagementClient.grantPermissions(username, readPattern);
        } catch (RuntimeException exception) {
            log.warn("订阅状态变更回滚后恢复MQ读取权限未完成，请人工核对：username={}", username, exception);
        }
    }

    private void cleanupDeletedMqResourcesQuietly(PushSubscription subscription) {
        try {
            rabbitManagementClient.deleteQueue(subscription.getMqQueue());
        } catch (RuntimeException exception) {
            log.warn("订阅删除后清理队列未完成，请人工核对：queue={}", subscription.getMqQueue(), exception);
        }
        try {
            rabbitManagementClient.deleteUser(subscription.getMqUsername());
        } catch (RuntimeException exception) {
            log.warn("订阅删除后清理账号未完成，请人工核对：username={}", subscription.getMqUsername(), exception);
        }
    }

    private List<Role> requireSubscribableRoles(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        List<Long> roleIds
    ) {
        List<Long> distinctRoleIds = roleIds == null
            ? List.of()
            : roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctRoleIds.isEmpty()) {
            throw new BizException("PUSH_SUBSCRIPTION_ROLE_REQUIRED", "请至少选择一个订阅角色");
        }
        List<Role> roles = new ArrayList<>();
        for (Long roleId : distinctRoleIds) {
            roles.add(roleRepository.findById(roleId)
                .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在")));
        }
        List<Role> visible = scopePolicy.filterVisibleRoles(subjectType, subjectId, roles);
        if (visible.size() != roles.size()) {
            throw new BizException("PUSH_SUBSCRIPTION_ROLE_FORBIDDEN", "订阅角色超出当前订阅主体的可见范围");
        }
        return visible;
    }

    private PushSubscription requireManageableSubscription(Long subscriptionId, AuthenticatedUser principal) {
        requireSession(principal);
        PushSubscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new BizException("PUSH_SUBSCRIPTION_NOT_FOUND", "订阅不存在"));
        if (subscription.getSubjectType() == PersonalAccessTokenSubjectType.GLOBAL) {
            requirePlatformAdmin(principal);
        } else {
            authorizationService.requireOwner(principal, subscription.getSubjectId());
        }
        return subscription;
    }

    private List<SubscriptionView> toViews(List<PushSubscription> subscriptions) {
        if (subscriptions.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> roleCounts = subscriptionRepository.countRolesBySubscriptionIds(
            subscriptions.stream().map(PushSubscription::getId).toList()
        );
        return subscriptions.stream()
            .map(subscription -> toView(subscription, roleCounts.getOrDefault(subscription.getId(), 0)))
            .toList();
    }

    private SubscriptionView toView(PushSubscription subscription, int roleCount) {
        return new SubscriptionView(
            subscription.getId(),
            subscription.getName(),
            subscription.getDescription(),
            subscription.getSubjectType(),
            subscription.getSubjectId(),
            subscription.getStatus(),
            roleCount,
            subscription.getCreator(),
            subscription.getGmtCreate(),
            subscription.getGmtModified()
        );
    }

    private SubscriptionMqInfo mqInfo(String queueName, String username, String password, String requestHost) {
        String externalHost = rabbitmqProperties.getConnection().getExternalHost();
        String host = externalHost == null || externalHost.isBlank() ? requestHost : externalHost;
        return new SubscriptionMqInfo(
            host,
            rabbitmqProperties.getConnection().getExternalPort(),
            rabbitmqProperties.getVhost(),
            queueName,
            username,
            password
        );
    }

    private String readPattern(String queueName) {
        return "^" + Pattern.quote(queueName) + "$";
    }

    private String generateMqPassword() {
        byte[] bytes = new byte[MQ_PASSWORD_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void requirePlatformAdmin(AuthenticatedUser principal) {
        if (!authorizationService.isPlatformAdmin(principal)) {
            throw new BizException("PUSH_SUBSCRIPTION_FORBIDDEN", "只有平台管理员可以管理全局订阅");
        }
    }

    private void requireSession(AuthenticatedUser principal) {
        if (principal == null
            || principal.credentialType() != CredentialType.SESSION
            || principal.id() == null
            || principal.userId() == null) {
            throw new BizException("AUTH_FORBIDDEN", "订阅管理仅支持网页登录会话");
        }
    }

    private String normalizeRequired(String value, String code, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BizException(code, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateLength(String value, int maxLength, String code, String message) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(code, message);
        }
    }

    private void audit(
        AuthenticatedUser principal,
        String sourceIp,
        String operationType,
        Long subscriptionId,
        String name
    ) {
        auditLogRepository.save(AuditLog.builder()
            .operator(principal.userId())
            .operatorIp(sourceIp)
            .operationType(operationType)
            .bizType("ROLE_PUSH_SUBSCRIPTION")
            .bizId(String.valueOf(subscriptionId))
            .afterJson(name)
            .result("SUCCESS")
            .build());
    }
}
