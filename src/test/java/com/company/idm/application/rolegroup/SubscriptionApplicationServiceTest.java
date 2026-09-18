package com.company.idm.application.rolegroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.token.CreatedPersonalAccessToken;
import com.company.idm.application.token.RoleSupplyTokenApplicationService;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.PushSubscription;
import com.company.idm.domain.rolegroup.PushSubscriptionRepository;
import com.company.idm.domain.rolegroup.PushSubscriptionScopeMode;
import com.company.idm.domain.rolegroup.PushSubscriptionStatus;
import com.company.idm.domain.rolegroup.RoleGroup;
import com.company.idm.domain.rolegroup.RoleGroupRepository;
import com.company.idm.domain.rolegroup.RoleSupplyEventRecorder;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.config.RabbitmqProperties;
import com.company.idm.infrastructure.config.RoleSupplyProperties;
import com.company.idm.infrastructure.rabbitmq.RabbitManagementClient;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 订阅应用服务测试：覆盖整组动态范围开通、绑定维护、生命周期终态与令牌换上下文。
 */
class SubscriptionApplicationServiceTest {

    private static final Long GROUP_ID = 10L;
    private static final Long SUBSCRIPTION_ID = 3L;
    private static final Long TOKEN_ID = 80L;

    private final PushSubscriptionRepository subscriptionRepository = mock(PushSubscriptionRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final RoleGroupRepository roleGroupRepository = mock(RoleGroupRepository.class);
    private final RoleGroupAuthorizationService authorizationService = mock(RoleGroupAuthorizationService.class);
    private final RoleSupplyTokenApplicationService tokenService = mock(RoleSupplyTokenApplicationService.class);
    private final RoleSupplyEventRecorder eventRecorder = mock(RoleSupplyEventRecorder.class);
    private final RabbitManagementClient rabbitManagementClient = mock(RabbitManagementClient.class);
    private final RabbitmqProperties rabbitmqProperties = new RabbitmqProperties();
    private final RoleSupplyProperties supplyProperties = new RoleSupplyProperties();
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final SubscriptionApplicationService service = new SubscriptionApplicationService(
        subscriptionRepository,
        roleRepository,
        roleGroupRepository,
        authorizationService,
        new RoleSupplyScopePolicy(),
        new RoleSupplySubscriptionResolver(subscriptionRepository),
        tokenService,
        eventRecorder,
        rabbitManagementClient,
        rabbitmqProperties,
        supplyProperties,
        auditLogRepository
    );

    @Test
    void groupSubscriptionIsDynamicScopedAndBindsTheWholeGroupPattern() {
        stubSuccessfulGroupCreation();

        CreatedSubscription created = service.createGroupSubscription(
            GROUP_ID, "制品平台", "整组动态订阅", session(), "127.0.0.1", "idm.example.com"
        );

        verify(authorizationService).requireOwner(session(), GROUP_ID);
        verify(rabbitManagementClient).ensureTopicExchange("idm.role-change");
        verify(rabbitManagementClient).ensureQueue(
            "idm.sub.3", Map.of("x-dead-letter-exchange", "idm.role-change.dlx")
        );
        verify(rabbitManagementClient).bindQueue("idm.sub.3", "idm.role-change", "rg.10.#");
        verify(rabbitManagementClient).createUser(eq("idm-sub-3"), anyString());
        verify(rabbitManagementClient).grantPermissions("idm-sub-3", "^\\Qidm.sub.3\\E$");
        // 整组动态范围不固化角色清单，因此不写选集，组内新增角色无需重绑或重签令牌。
        verify(subscriptionRepository, never()).replaceRoles(any(), any());

        ArgumentCaptor<PushSubscription> savedCaptor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getScopeMode()).isEqualTo(PushSubscriptionScopeMode.DYNAMIC_GROUP);
        assertThat(savedCaptor.getValue().getConfigVersion()).isEqualTo(1L);

        assertThat(created.credential().tokenSecret()).isEqualTo("idm_pat_secret");
        assertThat(created.credential().mq().host()).isEqualTo("idm.example.com");
        assertThat(created.credential().mq().queue()).isEqualTo("idm.sub.3");
        assertThat(created.subscription().scopeMode()).isEqualTo("DYNAMIC_GROUP");
        assertThat(created.subscription().status()).isEqualTo(PushSubscriptionStatus.ENABLED);
        assertThat(created.subscription().roleCount()).isEqualTo(2);
    }

    @Test
    void globalSubscriptionKeepsExplicitRoleSelectionAndPerRoleBindings() {
        when(authorizationService.isPlatformAdmin(session())).thenReturn(true);
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation
            .<PushSubscription>getArgument(0).toBuilder().id(4L).build());
        when(roleRepository.findById(11L)).thenReturn(Optional.of(groupRole(11L, "finance.reader", GROUP_ID)));
        when(subscriptionRepository.findRoleIds(4L)).thenReturn(List.of(11L));
        when(tokenService.createTokenFor(any(), any(), anyString(), anyString(), any(), anyString()))
            .thenReturn(new CreatedPersonalAccessToken(token(), "idm_pat_secret"));
        when(subscriptionRepository.updateProvisioned(
            eq(4L), eq(TOKEN_ID), eq("idm.sub.4"), eq("idm-sub-4"), anyString(), eq("delegate"), any()
        )).thenReturn(true);
        when(subscriptionRepository.findById(4L)).thenReturn(Optional.of(provisionedSubscription()
            .toBuilder()
            .id(4L)
            .subjectType(PersonalAccessTokenSubjectType.GLOBAL)
            .subjectId(null)
            .scopeMode(PushSubscriptionScopeMode.SELECTED_ROLES)
            .build()));

        CreatedSubscription created = service.createGlobalSubscription(
            "全局订阅", null, List.of(11L), session(), "127.0.0.1", "idm.example.com"
        );

        verify(subscriptionRepository).replaceRoles(4L, List.of(11L));
        verify(rabbitManagementClient).bindQueue("idm.sub.4", "idm.role-change", "rg.*.role.finance.reader");
        assertThat(created.subscription().scopeMode()).isEqualTo("SELECTED_ROLES");
    }

    @Test
    void globalSubscriptionStillRequiresAtLeastOneRole() {
        when(authorizationService.isPlatformAdmin(session())).thenReturn(true);

        assertThatThrownBy(() -> service.createGlobalSubscription(
            "全局订阅", null, List.of(), session(), "127.0.0.1", "idm.example.com"
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_ROLE_REQUIRED");

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void mqProvisioningFailureCleansUpResourcesAndRethrows() {
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation
            .<PushSubscription>getArgument(0).toBuilder().id(3L).build());
        when(tokenService.createTokenFor(any(), any(), anyString(), anyString(), any(), anyString()))
            .thenReturn(new CreatedPersonalAccessToken(token(), "idm_pat_secret"));
        doThrow(new BizException("RABBITMQ_MANAGEMENT_FAILED", "消息服务操作失败，请确认消息服务可用后重试"))
            .when(rabbitManagementClient).createUser(eq("idm-sub-3"), anyString());

        // 开通失败会触发事务回滚，MQ 资源补偿注册在回滚回调中，需要模拟事务环境验证。
        simulateTransactionRollback(() -> assertThatThrownBy(() -> service.createGroupSubscription(
            GROUP_ID, "制品平台", null, session(), "127.0.0.1", "idm.example.com"
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("RABBITMQ_MANAGEMENT_FAILED"));

        verify(rabbitManagementClient).deleteQueue("idm.sub.3");
        verify(rabbitManagementClient).deleteUser("idm-sub-3");
        verify(subscriptionRepository, never()).updateProvisioned(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void blankNameIsRejectedBeforeAnyProvisioning() {
        assertThatThrownBy(() -> service.createGroupSubscription(
            GROUP_ID, "  ", null, session(), "127.0.0.1", "idm.example.com"
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_NAME_REQUIRED");

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void disablingSubscriptionRevokesReadAccessClosesConnectionsAndBumpsControlVersion() {
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(provisionedSubscription()));
        when(subscriptionRepository.updateStatus(
            eq(SUBSCRIPTION_ID), eq(PushSubscriptionStatus.DISABLED), eq("delegate"), any(LocalDateTime.class)
        )).thenReturn(true);
        when(subscriptionRepository.bumpConfigVersion(eq(SUBSCRIPTION_ID), eq("delegate"), any()))
            .thenReturn(Optional.of(2L));

        simulateTransactionCommit(() -> service.disableSubscription(SUBSCRIPTION_ID, session(), "127.0.0.1"));

        verify(rabbitManagementClient).grantPermissions("idm-sub-3", "");
        verify(subscriptionRepository).updateStatus(
            eq(SUBSCRIPTION_ID), eq(PushSubscriptionStatus.DISABLED), eq("delegate"), any(LocalDateTime.class)
        );
        verify(rabbitManagementClient).closeUserConnections("idm-sub-3");
        verify(rabbitManagementClient, never()).deleteQueue(anyString());
        verify(eventRecorder).record(any());
    }

    @Test
    void enablingSubscriptionRestoresReadAccessAndReassertsScopeBindings() {
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(provisionedSubscription()
            .toBuilder()
            .status(PushSubscriptionStatus.DISABLED)
            .build()));
        when(subscriptionRepository.updateStatus(
            eq(SUBSCRIPTION_ID), eq(PushSubscriptionStatus.ENABLED), eq("delegate"), any(LocalDateTime.class)
        )).thenReturn(true);
        when(subscriptionRepository.bumpConfigVersion(eq(SUBSCRIPTION_ID), eq("delegate"), any()))
            .thenReturn(Optional.of(3L));
        when(rabbitManagementClient.listQueueBindingRoutingKeys("idm.sub.3")).thenReturn(List.of());

        service.enableSubscription(SUBSCRIPTION_ID, session(), "127.0.0.1");

        verify(rabbitManagementClient).grantPermissions("idm-sub-3", "^\\Qidm.sub.3\\E$");
        verify(rabbitManagementClient).bindQueue("idm.sub.3", "idm.role-change", "rg.10.#");
    }

    @Test
    void revokedSubscriptionCannotBeEnabledAgain() {
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(provisionedSubscription()
            .toBuilder()
            .status(PushSubscriptionStatus.REVOKED)
            .build()));

        assertThatThrownBy(() -> service.enableSubscription(SUBSCRIPTION_ID, session(), "127.0.0.1"))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_REVOKED");
    }

    @Test
    void statusConflictSurfacesAsSubscriptionConflict() {
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(provisionedSubscription()));

        assertThatThrownBy(() -> service.disableSubscription(SUBSCRIPTION_ID, session(), "127.0.0.1"))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_CONFLICT");
    }

    @Test
    void rotationReissuesTokenClosesOldConnectionsAndStoresTheSameNewPassword() {
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(provisionedSubscription()));
        when(tokenService.rotateToken(TOKEN_ID, PersonalAccessTokenSubjectType.ROLE_GROUP, GROUP_ID, session(),
            "127.0.0.1")).thenReturn(new CreatedPersonalAccessToken(token(), "idm_pat_rotated"));
        when(subscriptionRepository.updateMqPassword(eq(SUBSCRIPTION_ID), anyString(), eq("delegate"), any()))
            .thenReturn(true);
        when(subscriptionRepository.bumpConfigVersion(eq(SUBSCRIPTION_ID), eq("delegate"), any()))
            .thenReturn(Optional.of(2L));

        SubscriptionCredential credential = simulateTransactionCommitReturning(
            () -> service.rotateSubscription(SUBSCRIPTION_ID, session(), "127.0.0.1", "idm.example.com")
        );

        ArgumentCaptor<String> issuedPassword = ArgumentCaptor.forClass(String.class);
        verify(rabbitManagementClient).createUser(eq("idm-sub-3"), issuedPassword.capture());
        ArgumentCaptor<String> storedPassword = ArgumentCaptor.forClass(String.class);
        verify(subscriptionRepository).updateMqPassword(
            eq(SUBSCRIPTION_ID), storedPassword.capture(), eq("delegate"), any(LocalDateTime.class)
        );
        verify(rabbitManagementClient).closeUserConnections("idm-sub-3");

        assertThat(storedPassword.getValue()).isEqualTo(issuedPassword.getValue());
        assertThat(credential.tokenSecret()).isEqualTo("idm_pat_rotated");
        assertThat(credential.mq().password()).isEqualTo(issuedPassword.getValue());
    }

    @Test
    void deleteSubscriptionRevokesTokenClearsSecretAndKeepsTerminalRecord() {
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(provisionedSubscription()));
        when(subscriptionRepository.bumpConfigVersion(eq(SUBSCRIPTION_ID), eq("delegate"), any()))
            .thenReturn(Optional.of(2L));

        simulateTransactionCommit(() -> service.deleteSubscription(SUBSCRIPTION_ID, session(), "127.0.0.1"));

        verify(tokenService).deleteToken(
            TOKEN_ID, PersonalAccessTokenSubjectType.ROLE_GROUP, GROUP_ID, session(), "127.0.0.1"
        );
        verify(subscriptionRepository).updateStatus(
            eq(SUBSCRIPTION_ID), eq(PushSubscriptionStatus.REVOKED), eq("delegate"), any(LocalDateTime.class)
        );
        // 终态保留订阅行但清除敏感密码明文，避免旧凭据长期可用。
        verify(subscriptionRepository).updateMqPassword(eq(SUBSCRIPTION_ID), eq(null), eq("delegate"), any());
        verify(subscriptionRepository, never()).delete(any());
        verify(rabbitManagementClient).closeUserConnections("idm-sub-3");
        verify(rabbitManagementClient).deleteQueue("idm.sub.3");
        verify(rabbitManagementClient).deleteUser("idm-sub-3");
    }

    @Test
    void bindingMaintenanceReplacesLegacyPerRoleBindingsWithScopePattern() {
        when(rabbitManagementClient.listQueueBindingRoutingKeys("idm.sub.3"))
            .thenReturn(List.of("role.finance.reader", "rg.10.#"));

        service.ensureBindings(provisionedSubscription());

        verify(rabbitManagementClient).unbindQueue("idm.sub.3", "idm.role-change", "role.finance.reader");
        verify(rabbitManagementClient, never()).bindQueue("idm.sub.3", "idm.role-change", "rg.10.#");
    }

    @Test
    void contextReturnsConnectionCredentialsAndRoleCatalogForEnabledSubscription() {
        when(subscriptionRepository.findByAccessTokenId(TOKEN_ID))
            .thenReturn(Optional.of(provisionedSubscription()));
        when(roleGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(RoleGroup.builder()
            .id(GROUP_ID)
            .groupName("制品平台")
            .build()));
        when(roleRepository.findAll()).thenReturn(List.of(
            groupRole(11L, "ARTIFACTS_BUILD", GROUP_ID),
            groupRole(12L, "OTHER_GROUP_ROLE", 99L)
        ));

        RoleSupplyContext context = service.contextForToken(subscriptionToken(), "idm.example.com");

        assertThat(context.sourceId()).isEqualTo("corp-idm");
        assertThat(context.protocolVersion()).isEqualTo(2);
        assertThat(context.subscription().id()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(context.subscription().scopeMode()).isEqualTo("DYNAMIC_GROUP");
        assertThat(context.roleGroup().name()).isEqualTo("制品平台");
        assertThat(context.mq().queue()).isEqualTo("idm.sub.3");
        assertThat(context.mq().username()).isEqualTo("idm-sub-3");
        assertThat(context.mq().password()).isEqualTo("mq-password");
        assertThat(context.mq().host()).isEqualTo("idm.example.com");
        assertThat(context.roles()).extracting(RoleSupplyContext.RoleInfo::code)
            .containsExactly("ARTIFACTS_BUILD");
    }

    @Test
    void contextForDisabledSubscriptionExposesStatusWithoutSecretsOrCatalog() {
        when(subscriptionRepository.findByAccessTokenId(TOKEN_ID)).thenReturn(Optional.of(provisionedSubscription()
            .toBuilder()
            .status(PushSubscriptionStatus.DISABLED)
            .build()));

        RoleSupplyContext context = service.contextForToken(subscriptionToken(), "idm.example.com");

        assertThat(context.subscription().status()).isEqualTo("DISABLED");
        assertThat(context.mq()).isNull();
        assertThat(context.roles()).isEmpty();
    }

    @Test
    void contextRejectsTokensThatAreNotBoundToTheClaimedSubscription() {
        when(subscriptionRepository.findByAccessTokenId(TOKEN_ID)).thenReturn(Optional.of(provisionedSubscription()
            .toBuilder()
            .subjectId(99L)
            .build()));

        assertThatThrownBy(() -> service.contextForToken(subscriptionToken(), "idm.example.com"))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_SUPPLY_TOKEN_INVALID");
    }

    @Test
    void listGroupSubscriptionsCountsCurrentGroupRolesAndHidesRevokedOnes() {
        when(subscriptionRepository.findBySubject(PersonalAccessTokenSubjectType.ROLE_GROUP, GROUP_ID))
            .thenReturn(List.of(
                provisionedSubscription(),
                provisionedSubscription().toBuilder().id(9L).status(PushSubscriptionStatus.REVOKED).build()
            ));
        when(roleRepository.findAll()).thenReturn(List.of(
            groupRole(11L, "ARTIFACTS_BUILD", GROUP_ID),
            groupRole(12L, "ARTIFACTS_PACKAGE", GROUP_ID),
            groupRole(13L, "OTHER", 99L)
        ));

        List<SubscriptionView> views = service.listGroupSubscriptions(GROUP_ID, session());

        verify(authorizationService).requireOwner(session(), GROUP_ID);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).roleCount()).isEqualTo(2);
        assertThat(views.get(0).scopeMode()).isEqualTo("DYNAMIC_GROUP");
        assertThat(views.get(0).status()).isEqualTo(PushSubscriptionStatus.ENABLED);
    }

    @Test
    void globalSubscriptionManagementRequiresPlatformAdministrator() {
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(provisionedSubscription()
            .toBuilder()
            .subjectType(PersonalAccessTokenSubjectType.GLOBAL)
            .subjectId(null)
            .build()));
        when(authorizationService.isPlatformAdmin(session())).thenReturn(false);

        assertThatThrownBy(() -> service.deleteSubscription(SUBSCRIPTION_ID, session(), "127.0.0.1"))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_FORBIDDEN");

        verify(rabbitManagementClient, never()).deleteQueue(anyString());
    }

    /** 模拟事务回滚：动作执行后触发 afterCompletion(ROLLED_BACK)，验证回滚补偿路径。 */
    private void simulateTransactionRollback(Runnable action) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
            TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    /** 模拟事务提交：动作执行后依次触发 afterCommit 与 afterCompletion(COMMITTED)。 */
    private void simulateTransactionCommit(Runnable action) {
        simulateTransactionCommitReturning(() -> {
            action.run();
            return null;
        });
    }

    private <T> T simulateTransactionCommitReturning(java.util.function.Supplier<T> action) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            T result = action.get();
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);
            synchronizations.forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
            );
            return result;
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private void stubSuccessfulGroupCreation() {
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation
            .<PushSubscription>getArgument(0).toBuilder().id(SUBSCRIPTION_ID).build());
        when(roleRepository.findAll()).thenReturn(List.of(
            groupRole(11L, "ARTIFACTS_BUILD", GROUP_ID),
            groupRole(12L, "ARTIFACTS_PACKAGE", GROUP_ID)
        ));
        when(tokenService.createTokenFor(
            eq(PersonalAccessTokenSubjectType.ROLE_GROUP),
            eq(GROUP_ID),
            eq("制品平台"),
            anyString(),
            eq(session()),
            eq("127.0.0.1")
        )).thenReturn(new CreatedPersonalAccessToken(token(), "idm_pat_secret"));
        when(subscriptionRepository.updateProvisioned(
            eq(SUBSCRIPTION_ID), eq(TOKEN_ID), eq("idm.sub.3"), eq("idm-sub-3"), anyString(), eq("delegate"), any()
        )).thenReturn(true);
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(provisionedSubscription()));
    }

    private PushSubscription provisionedSubscription() {
        LocalDateTime now = LocalDateTime.now();
        return PushSubscription.builder()
            .id(SUBSCRIPTION_ID)
            .name("制品平台")
            .subjectType(PersonalAccessTokenSubjectType.ROLE_GROUP)
            .subjectId(GROUP_ID)
            .scopeMode(PushSubscriptionScopeMode.DYNAMIC_GROUP)
            .status(PushSubscriptionStatus.ENABLED)
            .accessTokenId(TOKEN_ID)
            .configVersion(1L)
            .mqQueue("idm.sub.3")
            .mqUsername("idm-sub-3")
            .mqPassword("mq-password")
            .creator("7")
            .modifier("7")
            .gmtCreate(now)
            .gmtModified(now)
            .build();
    }

    private Role groupRole(Long id, String roleCode, Long roleGroupId) {
        return Role.builder()
            .id(id)
            .roleCode(roleCode)
            .roleName(roleCode)
            .roleScope(RoleScope.GROUP)
            .roleGroupId(roleGroupId)
            .status(1)
            .build();
    }

    private PersonalAccessToken token() {
        return PersonalAccessToken.builder()
            .id(TOKEN_ID)
            .subjectType(PersonalAccessTokenSubjectType.ROLE_GROUP)
            .subjectId(GROUP_ID)
            .name("制品平台")
            .build();
    }

    private AuthenticatedUser subscriptionToken() {
        return new AuthenticatedUser(
            null,
            "role-supply:group:10",
            0,
            Set.of(),
            CredentialType.PERSONAL_ACCESS_TOKEN,
            TOKEN_ID,
            Set.of(),
            null,
            PersonalAccessTokenSubjectType.ROLE_GROUP,
            GROUP_ID
        );
    }

    private AuthenticatedUser session() {
        return new AuthenticatedUser(
            7L,
            "delegate",
            0,
            Set.of("NORMAL_USER"),
            CredentialType.SESSION,
            null,
            Set.of(),
            null,
            PersonalAccessTokenSubjectType.USER,
            7L
        );
    }
}
