package com.company.idm.application.rolegroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.PushSubscription;
import com.company.idm.domain.rolegroup.PushSubscriptionRepository;
import com.company.idm.domain.rolegroup.PushSubscriptionScopeMode;
import com.company.idm.domain.rolegroup.PushSubscriptionStatus;
import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.domain.rolegroup.RoleScopeVersionRepository;
import com.company.idm.domain.rolegroup.RoleSupplyEventType;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.domain.user.RoleSupplyMember;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.RoleSupplyProperties;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 角色供给查询服务测试：覆盖动态范围、一致快照边界、范围版本增量、保留窗口与订阅生命周期约束。
 */
class RoleSupplyApplicationServiceTest {

    private static final Long GROUP_ID = 10L;
    private static final Long TOKEN_ID = 11L;

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleMembershipChangeRepository changeRepository = mock(RoleMembershipChangeRepository.class);
    private final PushSubscriptionRepository subscriptionRepository = mock(PushSubscriptionRepository.class);
    private final RoleScopeVersionRepository scopeVersionRepository = mock(RoleScopeVersionRepository.class);
    private final RoleSupplyApplicationService service = new RoleSupplyApplicationService(
        roleRepository,
        userRepository,
        changeRepository,
        new RoleSupplyScopePolicy(),
        new RoleSupplySubscriptionResolver(subscriptionRepository),
        scopeVersionRepository,
        new RoleSupplyProperties()
    );

    @BeforeEach
    void setUp() {
        when(subscriptionRepository.findByAccessTokenId(TOKEN_ID))
            .thenReturn(Optional.of(subscription(PushSubscriptionStatus.ENABLED)));
    }

    @Test
    void groupSnapshotUsesScopeVersionBoundaryAndExcludesForeignAndGlobalRoles() {
        Role global = role(1L, "GLOBAL_USER", RoleScope.GLOBAL, null);
        Role owned = role(2L, "ARTIFACTS_BUILD", RoleScope.GROUP, GROUP_ID);
        Role foreign = role(3L, "GROUP_B", RoleScope.GROUP, 20L);
        Role system = role(4L, "SUPER_ADMIN", RoleScope.SYSTEM, null);
        when(roleRepository.findAll()).thenReturn(List.of(global, owned, foreign, system));
        when(scopeVersionRepository.currentVersion(GROUP_ID)).thenReturn(42L);
        when(userRepository.findRoleSupplyMembersByRoleId(2L))
            .thenReturn(List.of(new RoleSupplyMember("ou_lisi", "李四")));

        RoleSupplySnapshot result = service.snapshot(groupToken());

        assertThat(result.scopeVersion()).isEqualTo(42L);
        assertThat(result.snapshotCursor()).isEqualTo(42L);
        assertThat(result.scopeType()).isEqualTo("ROLE_GROUP");
        assertThat(result.roleGroupId()).isEqualTo(GROUP_ID);
        assertThat(result.complete()).isTrue();
        assertThat(result.hasMore()).isFalse();
        assertThat(result.roles()).extracting(RoleSupplyRoleSnapshot::roleCode)
            .containsExactly("ARTIFACTS_BUILD");
        assertThat(result.roles().get(0).memberNames()).containsExactly("李四");
        assertThat(result.roles().get(0).memberUserIds()).containsExactly("ou_lisi");
        assertThat(result.roles().get(0).roleStatus()).isEqualTo(1);
    }

    @Test
    void snapshotPaginationKeepsFrozenBoundaryAndReportsRemainingPages() {
        when(roleRepository.findAll()).thenReturn(List.of(
            role(2L, "ARTIFACTS_BUILD", RoleScope.GROUP, GROUP_ID),
            role(3L, "ARTIFACTS_PACKAGE", RoleScope.GROUP, GROUP_ID)
        ));
        when(scopeVersionRepository.currentVersion(GROUP_ID)).thenReturn(7L);
        when(userRepository.findRoleSupplyMembersByRoleId(2L)).thenReturn(List.of());
        when(userRepository.findRoleSupplyMembersByRoleId(3L)).thenReturn(List.of());

        RoleSupplySnapshot firstPage = service.snapshot(groupToken(), 1, 1);
        RoleSupplySnapshot secondPage = service.snapshot(groupToken(), 2, 1);

        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.scopeVersion()).isEqualTo(7L);
        assertThat(firstPage.roles()).extracting(RoleSupplyRoleSnapshot::roleCode)
            .containsExactly("ARTIFACTS_BUILD");
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.scopeVersion()).isEqualTo(7L);
        assertThat(secondPage.roles()).extracting(RoleSupplyRoleSnapshot::roleCode)
            .containsExactly("ARTIFACTS_PACKAGE");
    }

    @Test
    void groupChangesFollowScopeVersionAndReturnLastVersionAsCheckpoint() {
        RoleMembershipChange first = change(8L, RoleSupplyEventType.MEMBER_ADDED, "ADDED", 5L);
        RoleMembershipChange second = change(9L, RoleSupplyEventType.MEMBER_REMOVED, "REMOVED", 6L);
        RoleMembershipChange beyondPage = change(10L, RoleSupplyEventType.MEMBER_ADDED, "ADDED", 7L);
        when(changeRepository.earliestScopeVersion(GROUP_ID)).thenReturn(1L);
        when(changeRepository.findAfterScopeVersion(GROUP_ID, 4L, 3))
            .thenReturn(List.of(first, second, beyondPage));

        RoleSupplyChanges result = service.changes(groupToken(), 4L, 2);

        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(6L);
        assertThat(result.changes()).extracting(RoleMembershipChange::changeType)
            .containsExactly("ADDED", "REMOVED");
        assertThat(result.changes()).extracting(RoleMembershipChange::memberUserId)
            .containsExactly("张三-id", "李四-id");
    }

    @Test
    void groupChangesBeyondRetentionWindowRequireSnapshotRebuild() {
        when(changeRepository.earliestScopeVersion(GROUP_ID)).thenReturn(50L);

        assertThatThrownBy(() -> service.changes(groupToken(), 10L, 100))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_SUPPLY_CURSOR_EXPIRED");
    }

    @Test
    void emptyGroupChangesAdvanceCheckpointToCurrentScopeVersion() {
        when(changeRepository.earliestScopeVersion(GROUP_ID)).thenReturn(0L);
        when(changeRepository.findAfterScopeVersion(GROUP_ID, 9L, 101)).thenReturn(List.of());
        when(scopeVersionRepository.currentVersion(GROUP_ID)).thenReturn(12L);

        RoleSupplyChanges result = service.changes(groupToken(), 9L, 100);

        assertThat(result.changes()).isEmpty();
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isEqualTo(12L);
    }

    @Test
    void globalSubscriptionKeepsEventCursorSemanticsAndPageSizeCap() {
        when(subscriptionRepository.findByAccessTokenId(TOKEN_ID))
            .thenReturn(Optional.of(globalSubscription()));
        when(changeRepository.findAfter(0L, 501, PersonalAccessTokenSubjectType.GLOBAL, null))
            .thenReturn(List.of());
        when(changeRepository.currentCursor()).thenReturn(33L);

        RoleSupplyChanges result = service.changes(globalToken(), 0L, 50_000);

        assertThat(result.nextCursor()).isEqualTo(33L);
        verify(changeRepository).findAfter(0L, 501, PersonalAccessTokenSubjectType.GLOBAL, null);
    }

    @Test
    void disabledSubscriptionCannotFetchSupplyData() {
        when(subscriptionRepository.findByAccessTokenId(TOKEN_ID))
            .thenReturn(Optional.of(subscription(PushSubscriptionStatus.DISABLED)));

        assertThatThrownBy(() -> service.snapshot(groupToken()))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_SUPPLY_SUBSCRIPTION_DISABLED");
        assertThatThrownBy(() -> service.changes(groupToken(), 0L, 10))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_SUPPLY_SUBSCRIPTION_DISABLED");
    }

    @Test
    void rejectsSessionsUserTokensAndTokensWithoutSubscription() {
        assertThatThrownBy(() -> service.snapshot(session()))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_SUPPLY_TOKEN_REQUIRED");

        assertThatThrownBy(() -> service.changes(userToken(), 0L, 100))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_SUPPLY_TOKEN_REQUIRED");

        when(subscriptionRepository.findByAccessTokenId(TOKEN_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.snapshot(groupToken()))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_SUPPLY_SUBSCRIPTION_NOT_FOUND");
    }

    private PushSubscription subscription(PushSubscriptionStatus status) {
        return PushSubscription.builder()
            .id(4L)
            .name("制品平台")
            .subjectType(PersonalAccessTokenSubjectType.ROLE_GROUP)
            .subjectId(GROUP_ID)
            .scopeMode(PushSubscriptionScopeMode.DYNAMIC_GROUP)
            .status(status)
            .accessTokenId(TOKEN_ID)
            .configVersion(1L)
            .mqQueue("idm.sub.4")
            .mqUsername("idm-sub-4")
            .mqPassword("secret")
            .build();
    }

    private PushSubscription globalSubscription() {
        return PushSubscription.builder()
            .id(5L)
            .name("全局订阅")
            .subjectType(PersonalAccessTokenSubjectType.GLOBAL)
            .subjectId(null)
            .scopeMode(PushSubscriptionScopeMode.SELECTED_ROLES)
            .status(PushSubscriptionStatus.ENABLED)
            .accessTokenId(TOKEN_ID)
            .configVersion(1L)
            .mqQueue("idm.sub.5")
            .build();
    }

    private Role role(Long id, String code, RoleScope scope, Long groupId) {
        return Role.builder()
            .id(id)
            .roleCode(code)
            .roleName(code)
            .roleScope(scope)
            .roleGroupId(groupId)
            .status(1)
            .build();
    }

    private RoleMembershipChange change(Long id, RoleSupplyEventType eventType, String type, Long scopeVersion) {
        String memberName = "ADDED".equals(type) ? "张三" : "李四";
        return new RoleMembershipChange(
            id,
            eventType,
            2L,
            "ARTIFACTS_BUILD",
            "构建管理员",
            RoleScope.GROUP,
            GROUP_ID,
            scopeVersion,
            7L,
            memberName,
            memberName + "-id",
            type,
            null,
            null,
            null,
            LocalDateTime.now()
        );
    }

    private AuthenticatedUser groupToken() {
        return principal(CredentialType.PERSONAL_ACCESS_TOKEN, PersonalAccessTokenSubjectType.ROLE_GROUP, GROUP_ID);
    }

    private AuthenticatedUser globalToken() {
        return principal(CredentialType.PERSONAL_ACCESS_TOKEN, PersonalAccessTokenSubjectType.GLOBAL, null);
    }

    private AuthenticatedUser userToken() {
        return principal(CredentialType.PERSONAL_ACCESS_TOKEN, PersonalAccessTokenSubjectType.USER, 7L);
    }

    private AuthenticatedUser session() {
        return principal(CredentialType.SESSION, PersonalAccessTokenSubjectType.USER, 7L);
    }

    private AuthenticatedUser principal(
        CredentialType credentialType,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId
    ) {
        return new AuthenticatedUser(
            credentialType == CredentialType.SESSION ? 7L : null,
            "principal",
            0,
            Set.of(),
            credentialType,
            credentialType == CredentialType.PERSONAL_ACCESS_TOKEN ? TOKEN_ID : null,
            Set.of(),
            null,
            subjectType,
            subjectId
        );
    }
}
