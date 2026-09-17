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
import com.company.idm.domain.rolegroup.PushSubscriptionStatus;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.config.RabbitmqProperties;
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

class SubscriptionApplicationServiceTest {

    private final PushSubscriptionRepository subscriptionRepository = mock(PushSubscriptionRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final RoleGroupAuthorizationService authorizationService = mock(RoleGroupAuthorizationService.class);
    private final RoleSupplyTokenApplicationService tokenService = mock(RoleSupplyTokenApplicationService.class);
    private final RabbitManagementClient rabbitManagementClient = mock(RabbitManagementClient.class);
    private final RabbitmqProperties rabbitmqProperties = new RabbitmqProperties();
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final SubscriptionApplicationService service = new SubscriptionApplicationService(
        subscriptionRepository,
        roleRepository,
        authorizationService,
        new RoleSupplyScopePolicy(),
        tokenService,
        rabbitManagementClient,
        rabbitmqProperties,
        auditLogRepository
    );

    @Test
    void createsGroupSubscriptionProvisioningTokenQueueBindingsAndAccount() {
        stubSuccessfulCreation();

        CreatedSubscription created = service.createGroupSubscription(
            10L, "财务系统同步", "只读角色供给", List.of(11L), session(), "127.0.0.1", "idm.example.com"
        );

        verify(authorizationService).requireOwner(session(), 10L);
        verify(subscriptionRepository).replaceRoles(3L, List.of(11L));
        verify(rabbitManagementClient).ensureTopicExchange("idm.role-change");
        verify(rabbitManagementClient).ensureFanoutExchange("idm.role-change.dlx");
        verify(rabbitManagementClient).ensureQueue("idm.role-change.dlq", null);
        verify(rabbitManagementClient).bindQueue("idm.role-change.dlq", "idm.role-change.dlx", "");
        verify(rabbitManagementClient).ensureQueue(
            "idm.sub.3", Map.of("x-dead-letter-exchange", "idm.role-change.dlx")
        );
        verify(rabbitManagementClient).bindQueue("idm.sub.3", "idm.role-change", "role.finance.reader");
        verify(rabbitManagementClient).createUser(eq("idm-sub-3"), anyString());
        verify(rabbitManagementClient).grantPermissions("idm-sub-3", "^\\Qidm.sub.3\\E$");

        assertThat(created.credential().tokenSecret()).isEqualTo("idm_pat_secret");
        assertThat(created.credential().mq().host()).isEqualTo("idm.example.com");
        assertThat(created.credential().mq().port()).isEqualTo(5672);
        assertThat(created.credential().mq().vhost()).isEqualTo("/");
        assertThat(created.credential().mq().queue()).isEqualTo("idm.sub.3");
        assertThat(created.credential().mq().username()).isEqualTo("idm-sub-3");
        assertThat(created.credential().mq().password()).isNotBlank();
        assertThat(created.subscription().id()).isEqualTo(3L);
        assertThat(created.subscription().roleCount()).isEqualTo(1);
        assertThat(created.subscription().status()).isEqualTo(PushSubscriptionStatus.ENABLED);
    }

    @Test
    void mqProvisioningFailureCleansUpResourcesAndRethrows() {
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation
            .<PushSubscription>getArgument(0).toBuilder().id(3L).build());
        when(roleRepository.findById(11L)).thenReturn(Optional.of(groupRole(11L, "finance.reader", 10L)));
        when(tokenService.createTokenFor(any(), any(), anyString(), anyString(), any(), anyString()))
            .thenReturn(new CreatedPersonalAccessToken(token(), "idm_pat_secret"));
        doThrow(new BizException("RABBITMQ_MANAGEMENT_FAILED", "消息服务操作失败，请确认消息服务可用后重试"))
            .when(rabbitManagementClient).createUser(eq("idm-sub-3"), anyString());

        // 开通失败会触发事务回滚，MQ 资源补偿注册在回滚回调中，需要模拟事务环境验证。
        simulateTransactionRollback(() -> assertThatThrownBy(() -> service.createGroupSubscription(
            10L, "财务系统同步", null, List.of(11L), session(), "127.0.0.1", "idm.example.com"
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("RABBITMQ_MANAGEMENT_FAILED"));

        verify(rabbitManagementClient).deleteQueue("idm.sub.3");
        verify(rabbitManagementClient).deleteUser("idm-sub-3");
        verify(subscriptionRepository, never()).updateProvisioned(
            any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void successfulCreationCommitsWithoutCleanup() {
        stubSuccessfulCreation();

        simulateTransactionCommit(() -> service.createGroupSubscription(
            10L, "财务系统同步", null, List.of(11L), session(), "127.0.0.1", "idm.example.com"
        ));

        verify(rabbitManagementClient).createUser(eq("idm-sub-3"), anyString());
        verify(rabbitManagementClient, never()).deleteQueue(anyString());
        verify(rabbitManagementClient, never()).deleteUser(anyString());
    }

    @Test
    void roleOutsideTheSubscriptionSubjectsScopeIsRejectedBeforePersisting() {
        when(roleRepository.findById(11L)).thenReturn(Optional.of(groupRole(11L, "other.reader", 99L)));

        assertThatThrownBy(() -> service.createGroupSubscription(
            10L, "财务系统同步", null, List.of(11L), session(), "127.0.0.1", "idm.example.com"
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_ROLE_FORBIDDEN");

        verify(subscriptionRepository, never()).save(any());
        verify(rabbitManagementClient, never()).ensureTopicExchange(anyString());
    }

    @Test
    void subscriptionRequiresAtLeastOneRole() {
        assertThatThrownBy(() -> service.createGroupSubscription(
            10L, "财务系统同步", null, List.of(), session(), "127.0.0.1", "idm.example.com"
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_ROLE_REQUIRED");

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void blankNameIsRejectedBeforeAnyProvisioning() {
        assertThatThrownBy(() -> service.createGroupSubscription(
            10L, "  ", null, List.of(11L), session(), "127.0.0.1", "idm.example.com"
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_NAME_REQUIRED");

        verify(roleRepository, never()).findById(any());
    }

    @Test
    void disablingSubscriptionRevokesReadAccessButKeepsTheQueue() {
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(provisionedSubscription()));
        when(subscriptionRepository.updateStatus(
            eq(3L), eq(PushSubscriptionStatus.DISABLED), eq("delegate"), any(LocalDateTime.class)
        )).thenReturn(true);

        service.disableSubscription(3L, session(), "127.0.0.1");

        verify(rabbitManagementClient).grantPermissions("idm-sub-3", "");
        verify(subscriptionRepository).updateStatus(
            eq(3L), eq(PushSubscriptionStatus.DISABLED), eq("delegate"), any(LocalDateTime.class)
        );
        verify(rabbitManagementClient, never()).deleteQueue(anyString());
    }

    @Test
    void disablingAnAlreadyDisabledSubscriptionIsANoOp() {
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(provisionedSubscription()
            .toBuilder()
            .status(PushSubscriptionStatus.DISABLED)
            .build()));

        service.disableSubscription(3L, session(), "127.0.0.1");

        verify(rabbitManagementClient, never()).grantPermissions(anyString(), anyString());
        verify(subscriptionRepository, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void enablingSubscriptionRestoresExactQueueReadPattern() {
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(provisionedSubscription()
            .toBuilder()
            .status(PushSubscriptionStatus.DISABLED)
            .build()));
        when(subscriptionRepository.updateStatus(
            eq(3L), eq(PushSubscriptionStatus.ENABLED), eq("delegate"), any(LocalDateTime.class)
        )).thenReturn(true);

        service.enableSubscription(3L, session(), "127.0.0.1");

        verify(rabbitManagementClient).grantPermissions("idm-sub-3", "^\\Qidm.sub.3\\E$");
        verify(subscriptionRepository).updateStatus(
            eq(3L), eq(PushSubscriptionStatus.ENABLED), eq("delegate"), any(LocalDateTime.class)
        );
    }

    @Test
    void statusConflictSurfacesAsSubscriptionConflict() {
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(provisionedSubscription()));

        assertThatThrownBy(() -> service.disableSubscription(3L, session(), "127.0.0.1"))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_CONFLICT");
    }

    @Test
    void rotationReissuesTokenAndStoresTheSameNewMqPassword() {
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(provisionedSubscription()));
        when(tokenService.rotateToken(80L, PersonalAccessTokenSubjectType.ROLE_GROUP, 10L, session(), "127.0.0.1"))
            .thenReturn(new CreatedPersonalAccessToken(token(), "idm_pat_rotated"));
        when(subscriptionRepository.updateMqPassword(eq(3L), anyString(), eq("delegate"), any(LocalDateTime.class)))
            .thenReturn(true);

        SubscriptionCredential credential = service.rotateSubscription(3L, session(), "127.0.0.1", "idm.example.com");

        ArgumentCaptor<String> issuedPassword = ArgumentCaptor.forClass(String.class);
        verify(rabbitManagementClient).createUser(eq("idm-sub-3"), issuedPassword.capture());
        ArgumentCaptor<String> storedPassword = ArgumentCaptor.forClass(String.class);
        verify(subscriptionRepository).updateMqPassword(
            eq(3L), storedPassword.capture(), eq("delegate"), any(LocalDateTime.class)
        );

        assertThat(storedPassword.getValue()).isEqualTo(issuedPassword.getValue());
        assertThat(credential.tokenSecret()).isEqualTo("idm_pat_rotated");
        assertThat(credential.mq().password()).isEqualTo(issuedPassword.getValue());
    }

    @Test
    void revealReturnsStoredTokenSecretAndMqPasswordAfterVerification() {
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(provisionedSubscription()));
        when(tokenService.revealToken(
            80L, PersonalAccessTokenSubjectType.ROLE_GROUP, 10L, "verification-token", session(), "127.0.0.1"
        )).thenReturn("idm_pat_visible");

        SubscriptionCredential credential = service.revealSubscription(
            3L, "verification-token", session(), "127.0.0.1", "idm.example.com"
        );

        assertThat(credential.tokenSecret()).isEqualTo("idm_pat_visible");
        assertThat(credential.mq().queue()).isEqualTo("idm.sub.3");
        assertThat(credential.mq().password()).isEqualTo("mq-password");
    }

    @Test
    void deleteSubscriptionCleansQueueAccountTokenAndRow() {
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(provisionedSubscription()));

        service.deleteSubscription(3L, session(), "127.0.0.1");

        verify(rabbitManagementClient).deleteQueue("idm.sub.3");
        verify(rabbitManagementClient).deleteUser("idm-sub-3");
        verify(tokenService).deleteToken(
            80L, PersonalAccessTokenSubjectType.ROLE_GROUP, 10L, session(), "127.0.0.1"
        );
        verify(subscriptionRepository).delete(3L);
    }

    @Test
    void globalSubscriptionManagementRequiresPlatformAdministrator() {
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(provisionedSubscription()
            .toBuilder()
            .subjectType(PersonalAccessTokenSubjectType.GLOBAL)
            .subjectId(null)
            .build()));
        when(authorizationService.isPlatformAdmin(session())).thenReturn(false);

        assertThatThrownBy(() -> service.deleteSubscription(3L, session(), "127.0.0.1"))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_FORBIDDEN");

        verify(rabbitManagementClient, never()).deleteQueue(anyString());
    }

    @Test
    void listGroupSubscriptionsMapsRoleCounts() {
        when(subscriptionRepository.findBySubject(PersonalAccessTokenSubjectType.ROLE_GROUP, 10L))
            .thenReturn(List.of(provisionedSubscription()));
        when(subscriptionRepository.countRolesBySubscriptionIds(List.of(3L))).thenReturn(Map.of(3L, 2));

        List<SubscriptionView> views = service.listGroupSubscriptions(10L, session());

        verify(authorizationService).requireOwner(session(), 10L);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).roleCount()).isEqualTo(2);
        assertThat(views.get(0).status()).isEqualTo(PushSubscriptionStatus.ENABLED);
    }

    @Test
    void listGlobalSubscriptionsRequiresPlatformAdministrator() {
        when(authorizationService.isPlatformAdmin(session())).thenReturn(false);

        assertThatThrownBy(() -> service.listGlobalSubscriptions(session()))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PUSH_SUBSCRIPTION_FORBIDDEN");
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

    /** 模拟事务提交：动作执行后依次触发 afterCommit 与 afterCompletion(COMMITTED)，验证提交时不做补偿。 */
    private void simulateTransactionCommit(Runnable action) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);
            synchronizations.forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private void stubSuccessfulCreation() {
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation
            .<PushSubscription>getArgument(0).toBuilder().id(3L).build());
        when(roleRepository.findById(11L)).thenReturn(Optional.of(groupRole(11L, "finance.reader", 10L)));
        when(tokenService.createTokenFor(
            eq(PersonalAccessTokenSubjectType.ROLE_GROUP),
            eq(10L),
            eq("财务系统同步"),
            anyString(),
            eq(session()),
            eq("127.0.0.1")
        )).thenReturn(new CreatedPersonalAccessToken(token(), "idm_pat_secret"));
        when(subscriptionRepository.updateProvisioned(
            eq(3L), eq(80L), eq("idm.sub.3"), eq("idm-sub-3"), anyString(), eq("delegate"), any(LocalDateTime.class)
        )).thenReturn(true);
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(provisionedSubscription()));
    }

    private PushSubscription provisionedSubscription() {
        LocalDateTime now = LocalDateTime.now();
        return PushSubscription.builder()
            .id(3L)
            .name("财务系统同步")
            .subjectType(PersonalAccessTokenSubjectType.ROLE_GROUP)
            .subjectId(10L)
            .status(PushSubscriptionStatus.ENABLED)
            .accessTokenId(80L)
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
            .id(80L)
            .subjectType(PersonalAccessTokenSubjectType.ROLE_GROUP)
            .subjectId(10L)
            .name("财务系统同步")
            .build();
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
