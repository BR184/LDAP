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
import com.company.idm.domain.rolegroup.PushSubscriptionScopeMode;
import com.company.idm.domain.rolegroup.PushSubscriptionStatus;
import com.company.idm.domain.rolegroup.RoleGroup;
import com.company.idm.domain.rolegroup.RoleGroupRepository;
import com.company.idm.domain.rolegroup.RoleSupplyEventDraft;
import com.company.idm.domain.rolegroup.RoleSupplyEventRecorder;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.config.RabbitmqProperties;
import com.company.idm.infrastructure.config.RoleSupplyProperties;
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
 * 角色变更推送订阅：第三方在平台建立订阅后，平台经 RabbitMQ 主动推送范围内授权事实变化。
 *
 * <p>角色组订阅采用整组动态范围——只指定角色组与对接方，不固化角色清单，组内新增角色
 * 自动纳入、删除角色自动移出，队列绑定使用范围通配因此无需重绑或重签令牌；全局订阅
 * 保留显式角色选集与既有投递语义。订阅自动持有订阅令牌（用于上下文/快照/增量接口）与
 * 专属队列账号（用于消费推送），资源编排通过 RabbitMQ 管理 API 自动完成。
 * 生命周期变化（轮换、停用、启用、撤销）在收回数据供给与 MQ 能力的同时终止已建立连接，
 * 并推进配置控制版本、记录可重放的控制事件，使消费方能判别控制事实新旧。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionApplicationService {

    private static final String QUEUE_PREFIX = "idm.sub.";
    private static final String USERNAME_PREFIX = "idm-sub-";
    /** 整组动态订阅的绑定模式：覆盖该组当前与后续新增角色的全部事件。 */
    private static final String GROUP_BINDING_PATTERN = "rg.%d.#";
    /** 全局订阅按角色绑定：匹配任意范围下的同一角色编码。 */
    private static final String ROLE_BINDING_PATTERN = "rg.*.role.%s";
    /** 已废弃的旧式绑定前缀，升级时清理，避免残留绑定造成范围误判。 */
    private static final String LEGACY_ROUTING_KEY_PREFIX = "role.";
    private static final String DEAD_LETTER_EXCHANGE_ARGUMENT = "x-dead-letter-exchange";
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final int MQ_PASSWORD_BYTES = 24;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PushSubscriptionRepository subscriptionRepository;
    private final RoleRepository roleRepository;
    private final RoleGroupRepository roleGroupRepository;
    private final RoleGroupAuthorizationService authorizationService;
    private final RoleSupplyScopePolicy scopePolicy;
    private final RoleSupplySubscriptionResolver subscriptionResolver;
    private final RoleSupplyTokenApplicationService tokenService;
    private final RoleSupplyEventRecorder eventRecorder;
    private final RabbitManagementClient rabbitManagementClient;
    private final RabbitmqProperties rabbitmqProperties;
    private final RoleSupplyProperties supplyProperties;
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

    /**
     * 创建角色组订阅：订阅整个角色组，范围随组内角色动态变化。
     *
     * @param groupId 角色组标识
     * @param name 对接方命名
     * @param description 订阅说明
     * @param principal 网页会话主体，必须是组所有者
     * @param sourceIp 来源 IP
     * @param requestHost 请求主机，用于在未配置对外 MQ 主机时回退
     * @return 新建订阅与其一次性展示的凭据
     */
    @Transactional
    public CreatedSubscription createGroupSubscription(
        Long groupId,
        String name,
        String description,
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
            List.of(),
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

    /**
     * 轮换订阅凭据：同时更换订阅令牌与 MQ 密码，并使旧连接立即失效。
     *
     * <p>轮换属于同一订阅的凭据更新，不改变业务身份；配置控制版本递增，消费方据此
     * 判别控制事实新旧。旧令牌与旧密码在轮换后不可继续使用。
     */
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
        long configVersion = bumpConfigVersion(subscriptionId, principal.userId());
        recordControlEvent(subscription, "ROTATED", configVersion, principal.userId());
        // 旧密码连接在权限未变时不会自动断开，必须显式终止，否则旧消费者仍可继续读取。
        registerAfterCommit(() -> closeConnectionsQuietly(subscription.getMqUsername()));
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

    /**
     * 停用订阅：同时限制 HTTP 数据供给与 MQ 消费，并终止已建立连接。
     *
     * <p>停用后订阅令牌仍可取得非敏感状态（供接入方展示“订阅已停用”），但快照与增量
     * 请求被拒绝；队列继续积压消息，恢复启用后消费方可续收。
     */
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
        long configVersion = bumpConfigVersion(subscriptionId, principal.userId());
        recordControlEvent(subscription, "DISABLED", configVersion, principal.userId());
        registerAfterCommit(() -> closeConnectionsQuietly(subscription.getMqUsername()));
        audit(principal, sourceIp, "PUSH_SUBSCRIPTION_DISABLE", subscriptionId, subscription.getName());
    }

    /** 启用订阅：恢复 MQ 读取权限、幂等确保范围绑定，并推进配置控制版本。 */
    @Transactional
    public void enableSubscription(Long subscriptionId, AuthenticatedUser principal, String sourceIp) {
        PushSubscription subscription = requireManageableSubscription(subscriptionId, principal);
        if (subscription.getStatus() == PushSubscriptionStatus.ENABLED) {
            return;
        }
        if (subscription.getStatus() == PushSubscriptionStatus.REVOKED) {
            throw new BizException("PUSH_SUBSCRIPTION_REVOKED", "订阅已撤销，不能重新启用");
        }
        // 先授予 MQ 读取权限，事务回滚时收回，保证订阅状态与消费者实际能力一致。
        registerOnRollback(() -> restorePermissionQuietly(subscription.getMqUsername(), ""));
        rabbitManagementClient.grantPermissions(subscription.getMqUsername(), readPattern(subscription.getMqQueue()));
        ensureBindings(subscription);
        if (!subscriptionRepository.updateStatus(
            subscriptionId, PushSubscriptionStatus.ENABLED, principal.userId(), LocalDateTime.now())) {
            throw new BizException("PUSH_SUBSCRIPTION_CONFLICT", "订阅状态已变化，请刷新后重试");
        }
        long configVersion = bumpConfigVersion(subscriptionId, principal.userId());
        recordControlEvent(subscription, "ENABLED", configVersion, principal.userId());
        audit(principal, sourceIp, "PUSH_SUBSCRIPTION_ENABLE", subscriptionId, subscription.getName());
    }

    /**
     * 撤销订阅：吊销令牌、清理 MQ 资源，并保留终态记录阻止旧连接与旧请求继续生效。
     *
     * <p>订阅行转为终态而不是物理删除，使旧令牌请求能得到明确的“已撤销”结果；
     * 敏感 MQ 密码明文同时清除，不因保留终态而长期留存秘密。
     */
    @Transactional
    public void deleteSubscription(Long subscriptionId, AuthenticatedUser principal, String sourceIp) {
        PushSubscription subscription = requireManageableSubscription(subscriptionId, principal);
        if (subscription.getStatus() == PushSubscriptionStatus.REVOKED) {
            return;
        }
        if (subscription.getAccessTokenId() != null) {
            tokenService.deleteToken(
                subscription.getAccessTokenId(),
                subscription.getSubjectType(),
                subscription.getSubjectId(),
                principal,
                sourceIp
            );
        }
        long configVersion = bumpConfigVersion(subscriptionId, principal.userId());
        recordControlEvent(subscription, "REVOKED", configVersion, principal.userId());
        subscriptionRepository.updateStatus(
            subscriptionId, PushSubscriptionStatus.REVOKED, principal.userId(), LocalDateTime.now());
        subscriptionRepository.updateMqPassword(subscriptionId, null, principal.userId(), LocalDateTime.now());
        audit(principal, sourceIp, "PUSH_SUBSCRIPTION_DELETE", subscriptionId, subscription.getName());
        // 队列与账号属不可恢复资源，改为数据库提交后再清理；清理失败仅告警并提示人工核对。
        registerAfterCommit(() -> {
            closeConnectionsQuietly(subscription.getMqUsername());
            cleanupDeletedMqResourcesQuietly(subscription);
        });
    }

    /**
     * 按已认证订阅令牌返回接入上下文。
     *
     * <p>接入方只需提交令牌即可取得来源身份、订阅与角色组身份、协议能力、非敏感状态
     * 与专属连接凭据，无需人工抄录 MQ 参数。仅返回令牌自己所属订阅的信息；订阅停用
     * 或撤销时只返回非敏感状态，不再供应连接凭据与角色目录。秘密字段不缓存、不入日志。
     *
     * @param principal 已认证的订阅令牌主体
     * @param requestHost 请求主机，用于在未配置对外 MQ 主机时回退
     * @return 订阅上下文
     */
    public RoleSupplyContext contextForToken(AuthenticatedUser principal, String requestHost) {
        PushSubscription subscription = subscriptionResolver.resolve(principal);
        boolean enabled = subscription.isEnabled();
        Long groupId = subscriptionResolver.groupScopeOf(subscription);
        RoleSupplyContext.RoleGroupInfo roleGroup = groupId == null
            ? null
            : roleGroupRepository.findById(groupId)
                .map(group -> new RoleSupplyContext.RoleGroupInfo(group.getId(), group.getGroupName()))
                .orElse(null);
        RoleSupplyContext.MqConnection mq = enabled && subscription.getMqQueue() != null
            ? new RoleSupplyContext.MqConnection(
                resolveMqHost(requestHost),
                rabbitmqProperties.getConnection().getExternalPort(),
                rabbitmqProperties.getVhost(),
                subscription.getMqQueue(),
                subscription.getMqUsername(),
                subscription.getMqPassword()
            )
            : null;
        List<RoleSupplyContext.RoleInfo> roles = enabled
            ? scopePolicy.filterVisibleRoles(
                subscription.getSubjectType(), subscription.getSubjectId(), roleRepository.findAll())
                .stream()
                .map(role -> new RoleSupplyContext.RoleInfo(
                    role.getId(), role.getRoleCode(), role.getRoleName(), role.getStatus()))
                .toList()
            : List.of();
        return new RoleSupplyContext(
            supplyProperties.getProtocolVersion(),
            supplyProperties.getSourceId(),
            new RoleSupplyContext.SubscriptionInfo(
                subscription.getId(),
                subscription.getName(),
                subscription.getStatus().name(),
                subscription.getScopeMode() == null ? null : subscription.getScopeMode().name(),
                subscription.getConfigVersion()
            ),
            roleGroup,
            mq,
            roles
        );
    }

    /**
     * 幂等确保订阅的队列绑定与其范围模式一致，并清理已废弃的旧式绑定。
     *
     * <p>启动时对全部启用订阅执行，使升级后的整组动态订阅立即获得范围绑定；
     * 管理接口不可用时仅告警，不影响应用启动。
     *
     * @param subscription 目标订阅
     */
    public void ensureBindings(PushSubscription subscription) {
        String queue = subscription.getMqQueue();
        if (queue == null || queue.isBlank()) {
            return;
        }
        String exchange = rabbitmqProperties.getExchange();
        List<String> expected = expectedRoutingKeys(subscription);
        List<String> existing;
        try {
            existing = rabbitManagementClient.listQueueBindingRoutingKeys(queue);
        } catch (RuntimeException exception) {
            log.warn("读取订阅队列绑定失败，跳过绑定维护：queue={}", queue, exception);
            return;
        }
        for (String routingKey : expected) {
            if (!existing.contains(routingKey)) {
                rabbitManagementClient.bindQueue(queue, exchange, routingKey);
            }
        }
        for (String routingKey : existing) {
            boolean obsolete = routingKey.startsWith(LEGACY_ROUTING_KEY_PREFIX) || !expected.contains(routingKey);
            if (obsolete && !routingKey.isBlank()) {
                rabbitManagementClient.unbindQueue(queue, exchange, routingKey);
            }
        }
    }

    /** 计算订阅应有的绑定路由键集合。 */
    public List<String> expectedRoutingKeys(PushSubscription subscription) {
        if (subscription.getScopeMode() == PushSubscriptionScopeMode.DYNAMIC_GROUP
            && subscription.getSubjectId() != null) {
            return List.of(String.format(GROUP_BINDING_PATTERN, subscription.getSubjectId()));
        }
        List<String> keys = new ArrayList<>();
        for (Long roleId : subscriptionRepository.findRoleIds(subscription.getId())) {
            roleRepository.findById(roleId)
                .ifPresent(role -> keys.add(String.format(ROLE_BINDING_PATTERN, role.getRoleCode())));
        }
        // 全局订阅同样需要接收订阅控制与角色组展示事件，否则生命周期变化只能靠心跳与快照兜底。
        keys.add("rg.*.subscription");
        keys.add("rg.*.group");
        return List.copyOf(keys);
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
        boolean dynamicGroup = subjectType == PersonalAccessTokenSubjectType.ROLE_GROUP;
        List<Role> roles = dynamicGroup ? List.of() : requireSubscribableRoles(subjectType, subjectId, roleIds);
        LocalDateTime now = LocalDateTime.now();
        PushSubscription saved = subscriptionRepository.save(PushSubscription.builder()
            .name(normalizedName)
            .description(normalizedDescription)
            .subjectType(subjectType)
            .subjectId(subjectId)
            .scopeMode(dynamicGroup ? PushSubscriptionScopeMode.DYNAMIC_GROUP : PushSubscriptionScopeMode.SELECTED_ROLES)
            .status(PushSubscriptionStatus.ENABLED)
            .configVersion(1L)
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
        if (!dynamicGroup) {
            subscriptionRepository.replaceRoles(saved.getId(), roles.stream().map(Role::getId).toList());
        }
        PushSubscription provisionedScope = saved.toBuilder().mqQueue(queueName).build();
        provisionMqResources(queueName, username, password, provisionedScope);
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
            toView(provisioned, dynamicGroup ? countGroupRoles(subjectId) : roles.size()),
            new SubscriptionCredential(
                saved.getId(),
                tokenSecret,
                mqInfo(queueName, username, password, requestHost)
            )
        );
    }

    private void provisionMqResources(
        String queueName,
        String username,
        String password,
        PushSubscription subscription
    ) {
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
        for (String routingKey : expectedRoutingKeys(subscription)) {
            rabbitManagementClient.bindQueue(queueName, rabbitmqProperties.getExchange(), routingKey);
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

    private void closeConnectionsQuietly(String username) {
        try {
            rabbitManagementClient.closeUserConnections(username);
        } catch (RuntimeException exception) {
            log.warn("终止订阅旧连接未完成，请人工核对：username={}", username, exception);
        }
    }

    private void cleanupDeletedMqResourcesQuietly(PushSubscription subscription) {
        try {
            rabbitManagementClient.deleteQueue(subscription.getMqQueue());
        } catch (RuntimeException exception) {
            log.warn("订阅撤销后清理队列未完成，请人工核对：queue={}", subscription.getMqQueue(), exception);
        }
        try {
            rabbitManagementClient.deleteUser(subscription.getMqUsername());
        } catch (RuntimeException exception) {
            log.warn("订阅撤销后清理账号未完成，请人工核对：username={}", subscription.getMqUsername(), exception);
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

    private long bumpConfigVersion(Long subscriptionId, String operator) {
        return subscriptionRepository.bumpConfigVersion(subscriptionId, operator, LocalDateTime.now())
            .orElseThrow(() -> new BizException("PUSH_SUBSCRIPTION_CONFLICT", "订阅状态已变化，请刷新后重试"));
    }

    /** 记录订阅生命周期控制事件：可重放，且不携带新令牌或 MQ 密码。 */
    private void recordControlEvent(PushSubscription subscription, String action, long controlVersion, String operator) {
        eventRecorder.record(RoleSupplyEventDraft.subscriptionControl(
            subscription.getId(),
            subscriptionResolver.groupScopeOf(subscription),
            action,
            controlVersion,
            operator
        ));
    }

    private List<SubscriptionView> toViews(List<PushSubscription> subscriptions) {
        List<PushSubscription> active = subscriptions.stream()
            .filter(subscription -> subscription.getStatus() != PushSubscriptionStatus.REVOKED)
            .toList();
        if (active.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> roleCounts = subscriptionRepository.countRolesBySubscriptionIds(
            active.stream().map(PushSubscription::getId).toList()
        );
        return active.stream()
            .map(subscription -> toView(
                subscription,
                subscription.isDynamicGroupScope()
                    ? countGroupRoles(subscription.getSubjectId())
                    : roleCounts.getOrDefault(subscription.getId(), 0)
            ))
            .toList();
    }

    private SubscriptionView toView(PushSubscription subscription, int roleCount) {
        return SubscriptionView.of(subscription, roleCount);
    }

    /** 统计角色组当前角色数：整组动态订阅的范围大小。 */
    private int countGroupRoles(Long groupId) {
        if (groupId == null) {
            return 0;
        }
        return scopePolicy.filterVisibleRoles(
            PersonalAccessTokenSubjectType.ROLE_GROUP, groupId, roleRepository.findAll()).size();
    }

    private SubscriptionMqInfo mqInfo(String queueName, String username, String password, String requestHost) {
        return new SubscriptionMqInfo(
            resolveMqHost(requestHost),
            rabbitmqProperties.getConnection().getExternalPort(),
            rabbitmqProperties.getVhost(),
            queueName,
            username,
            password
        );
    }

    /** 对外 MQ 主机：优先部署显式配置的对外地址，未配置时回退请求主机。 */
    private String resolveMqHost(String requestHost) {
        String externalHost = rabbitmqProperties.getConnection().getExternalHost();
        return externalHost == null || externalHost.isBlank() ? requestHost : externalHost;
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
