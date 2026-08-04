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
import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.domain.user.PublicUser;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleSupplyApplicationServiceTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleMembershipChangeRepository changeRepository = mock(RoleMembershipChangeRepository.class);
    private final RoleSupplyApplicationService service = new RoleSupplyApplicationService(
        roleRepository,
        userRepository,
        changeRepository,
        new RoleSupplyScopePolicy()
    );

    @Test
    void groupSnapshotContainsOnlyGlobalAndOwnedGroupRoles() {
        Role global = role(1L, "GLOBAL_USER", RoleScope.GLOBAL, null);
        Role owned = role(2L, "GROUP_A", RoleScope.GROUP, 10L);
        Role foreign = role(3L, "GROUP_B", RoleScope.GROUP, 20L);
        Role system = role(4L, "SYSTEM_ADMIN", RoleScope.SYSTEM, null);
        when(changeRepository.currentCursor()).thenReturn(42L);
        when(roleRepository.findAll()).thenReturn(List.of(global, owned, foreign, system));
        when(userRepository.findPublicUsersByRoleId(1L)).thenReturn(List.of(new PublicUser(7L, "张三")));
        when(userRepository.findPublicUsersByRoleId(2L)).thenReturn(List.of(new PublicUser(8L, "李四")));

        RoleSupplySnapshot result = service.snapshot(groupToken(10L));

        assertThat(result.snapshotCursor()).isEqualTo(42L);
        assertThat(result.roles()).extracting(RoleSupplyRoleSnapshot::roleCode)
            .containsExactly("GLOBAL_USER", "GROUP_A");
        assertThat(result.roles().get(0).memberNames()).containsExactly("张三");
        assertThat(result.roles().get(1).memberNames()).containsExactly("李四");
    }

    @Test
    void changesPreserveAddedAndRemovedEventsAndExposePagination() {
        RoleMembershipChange added = change(8L, "ADDED", "张三");
        RoleMembershipChange removed = change(9L, "REMOVED", "李四");
        RoleMembershipChange nextPage = change(10L, "ADDED", "王五");
        when(changeRepository.findAfter(
            7L, 3, PersonalAccessTokenSubjectType.ROLE_GROUP, 10L
        )).thenReturn(List.of(added, removed, nextPage));

        RoleSupplyChanges result = service.changes(groupToken(10L), 7L, 2);

        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(9L);
        assertThat(result.changes()).extracting(RoleMembershipChange::changeType)
            .containsExactly("ADDED", "REMOVED");
    }

    @Test
    void changePageSizeIsCappedAtFiveHundred() {
        when(changeRepository.findAfter(
            0L, 501, PersonalAccessTokenSubjectType.GLOBAL, null
        )).thenReturn(List.of());
        when(changeRepository.currentCursor()).thenReturn(33L);

        RoleSupplyChanges result = service.changes(globalToken(), 0L, 50_000);

        assertThat(result.nextCursor()).isEqualTo(33L);
        verify(changeRepository).findAfter(0L, 501, PersonalAccessTokenSubjectType.GLOBAL, null);
    }

    @Test
    void rejectsSessionsAndUserPersonalAccessTokens() {
        assertThatThrownBy(() -> service.snapshot(session()))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_SUPPLY_TOKEN_REQUIRED");

        assertThatThrownBy(() -> service.changes(userToken(), 0L, 100))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_SUPPLY_TOKEN_REQUIRED");
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

    private RoleMembershipChange change(Long id, String type, String memberName) {
        return new RoleMembershipChange(
            id, 2L, "GROUP_A", "Group A", RoleScope.GROUP, 10L, memberName, type, LocalDateTime.now()
        );
    }

    private AuthenticatedUser groupToken(Long groupId) {
        return principal(CredentialType.PERSONAL_ACCESS_TOKEN, PersonalAccessTokenSubjectType.ROLE_GROUP, groupId);
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
            credentialType == CredentialType.PERSONAL_ACCESS_TOKEN ? 11L : null,
            Set.of(),
            null,
            subjectType,
            subjectId
        );
    }
}
