package com.company.idm.application.rolegroup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rolegroup.RoleGroupMember;
import com.company.idm.domain.rolegroup.RoleGroupMemberRole;
import com.company.idm.domain.rolegroup.RoleGroupRepository;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleGroupAuthorizationServiceTest {

    private final EffectivePermissionService permissionService = mock(EffectivePermissionService.class);
    private final RoleGroupRepository roleGroupRepository = mock(RoleGroupRepository.class);
    private final RoleGroupAuthorizationService service = new RoleGroupAuthorizationService(
        permissionService,
        roleGroupRepository,
        new DelegatedPermissionPairPolicy()
    );

    @Test
    void requiresBothMembershipAndDelegatedPermissionsForRoleManagement() {
        AuthenticatedUser principal = principal("delegate", Set.of("NORMAL_USER"));
        when(permissionService.resolve(principal)).thenReturn(Set.of(
            DelegatedPermissionPairPolicy.ROLE_GROUP_MANAGE,
            DelegatedPermissionPairPolicy.ROLE_GROUP_USER_ASSIGN
        ));
        when(roleGroupRepository.findMember(9L, 7L)).thenReturn(Optional.of(member(RoleGroupMemberRole.MANAGER)));

        assertThatCode(() -> service.requireRoleManager(principal, 9L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvitedMembersWithoutDelegatedPermissions() {
        AuthenticatedUser principal = principal("invited", Set.of("NORMAL_USER"));
        when(permissionService.resolve(principal)).thenReturn(Set.of());
        when(roleGroupRepository.findMember(9L, 7L)).thenReturn(Optional.of(member(RoleGroupMemberRole.MANAGER)));

        assertThatThrownBy(() -> service.requireRoleManager(principal, 9L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("委派权限");
    }

    @Test
    void restrictsGroupSettingsMembersAndTokensToOwners() {
        AuthenticatedUser principal = principal("manager", Set.of("NORMAL_USER"));
        when(permissionService.resolve(principal)).thenReturn(Set.of(
            DelegatedPermissionPairPolicy.ROLE_GROUP_MANAGE,
            DelegatedPermissionPairPolicy.ROLE_GROUP_USER_ASSIGN
        ));
        when(roleGroupRepository.findMember(9L, 7L)).thenReturn(Optional.of(member(RoleGroupMemberRole.MANAGER)));

        assertThatThrownBy(() -> service.requireOwner(principal, 9L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("所有者");
    }

    @Test
    void allowsPlatformAdministratorsToSuperviseEveryGroup() {
        AuthenticatedUser principal = principal("admin", Set.of("ADMIN"));

        assertThatCode(() -> service.requireOwner(principal, 999L)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireRoleManager(principal, 999L)).doesNotThrowAnyException();
    }

    private AuthenticatedUser principal(String userId, Set<String> roleCodes) {
        return new AuthenticatedUser(
            7L, userId, 0, roleCodes, CredentialType.SESSION, null, Set.of(), null,
            PersonalAccessTokenSubjectType.USER, 7L
        );
    }

    private RoleGroupMember member(RoleGroupMemberRole memberRole) {
        return new RoleGroupMember(9L, 7L, "delegate", memberRole);
    }
}
