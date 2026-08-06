package com.company.idm.application.rolegroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.RoleGroup;
import com.company.idm.domain.rolegroup.RoleGroupMember;
import com.company.idm.domain.rolegroup.RoleGroupMemberRole;
import com.company.idm.domain.rolegroup.RoleGroupRepository;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleGroupApplicationServiceTest {

    private final RoleGroupRepository roleGroupRepository = mock(RoleGroupRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final PolicyRefreshService policyRefreshService = mock(PolicyRefreshService.class);
    private final RoleGroupAuthorizationService authorizationService = mock(RoleGroupAuthorizationService.class);
    private final PersonalAccessTokenRepository tokenRepository = mock(PersonalAccessTokenRepository.class);
    private final RoleGroupApplicationService service = new RoleGroupApplicationService(
        roleGroupRepository,
        roleRepository,
        userRepository,
        auditLogRepository,
        policyRefreshService,
        authorizationService,
        tokenRepository
    );

    @BeforeEach
    void setUp() {
        when(roleGroupRepository.findById(10L)).thenReturn(Optional.of(RoleGroup.builder()
            .id(10L)
            .groupName("研发协作组")
            .status(1)
            .build()));
    }

    @Test
    void delegatedManagersCannotAssignManagementLevelRoles() {
        Role administrator = role(2L, "ADMIN", RoleScope.GLOBAL, null, 2);
        when(roleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(administrator));
        when(authorizationService.isPlatformAdmin(session())).thenReturn(false);

        assertThatThrownBy(() -> service.addRoleMembers(10L, 2L, List.of(8L), session()))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_GROUP_ROLE_ASSIGN_FORBIDDEN");

        verify(userRepository, never()).addRole(any(), any(), any());
    }

    @Test
    void hiddenSubmissionCannotAssignAResignedUser() {
        Role groupRole = role(20L, "PROJECT_REVIEWER", RoleScope.GROUP, 10L, 999);
        when(roleRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(groupRole));
        when(userRepository.findById(8L)).thenReturn(Optional.of(User.builder()
            .id(8L)
            .userId("former")
            .realName("离职员工")
            .employmentStatus(EmploymentStatus.RESIGNED)
            .build()));

        assertThatThrownBy(() -> service.addRoleMembers(10L, 20L, List.of(8L), session()))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("USER_NOT_ACTIVE");

        verify(userRepository, never()).addRole(any(), any(), any());
    }

    @Test
    void createdGroupRolesHaveAGroupScopeAndNoPlatformPermissionBinding() {
        when(roleRepository.findByCode("A")).thenReturn(Optional.empty());
        when(roleRepository.save(any())).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            return Role.builder()
                .id(30L)
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .permissionLevel(role.getPermissionLevel())
                .builtIn(role.getBuiltIn())
                .status(role.getStatus())
                .remark(role.getRemark())
                .roleScope(role.getRoleScope())
                .roleGroupId(role.getRoleGroupId())
                .build();
        });

        RoleGroupRoleView created = service.createRole(10L, "a", "项目观察员", null, session());

        assertThat(created.roleScope()).isEqualTo(RoleScope.GROUP);
        assertThat(created.roleGroupId()).isEqualTo(10L);
        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRoleCode()).isEqualTo("A");
        assertThat(roleCaptor.getValue().getPermissionLevel()).isEqualTo(999);
        verify(roleRepository, never()).assignPermissions(any(), any());
    }

    @Test
    void keepsTheLastOwnerAfterLockingTheGroupForUpdate() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder()
            .id(7L)
            .userId("delegate")
            .realName("委派管理员")
            .employmentStatus(EmploymentStatus.ACTIVE)
            .build()));
        when(roleGroupRepository.findMember(10L, 7L)).thenReturn(Optional.of(
            new RoleGroupMember(10L, 7L, "委派管理员", RoleGroupMemberRole.OWNER)
        ));
        when(roleGroupRepository.countOwners(10L)).thenReturn(1L);

        assertThatThrownBy(() -> service.saveCollaborator(
            10L, 7L, RoleGroupMemberRole.MANAGER, session()
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("ROLE_GROUP_OWNER_REQUIRED");

        verify(roleGroupRepository).lockById(10L);
        verify(roleGroupRepository, never()).saveMember(any(), any());
    }

    @Test
    void exposesCreatorAndCurrentOwnerNamesWithoutConfusingViewerIdentity() {
        RoleGroup group = RoleGroup.builder()
            .id(10L)
            .groupName("研发协作组")
            .creator("creator-user")
            .status(1)
            .build();
        when(roleGroupRepository.findAll()).thenReturn(List.of(group));
        when(authorizationService.isPlatformAdmin(session())).thenReturn(true);
        when(userRepository.findByUserId("creator-user")).thenReturn(Optional.of(User.builder()
            .id(6L)
            .userId("creator-user")
            .realName("创建人")
            .build()));
        when(roleGroupRepository.findMembers(10L)).thenReturn(List.of(
            new RoleGroupMember(10L, 6L, "创建人", RoleGroupMemberRole.OWNER),
            new RoleGroupMember(10L, 9L, "协管员", RoleGroupMemberRole.MANAGER),
            new RoleGroupMember(10L, 8L, "共同所有者", RoleGroupMemberRole.OWNER)
        ));

        RoleGroupView view = service.listGroups(session()).get(0);

        assertThat(view.creatorName()).isEqualTo("创建人");
        assertThat(view.ownerNames()).containsExactly("创建人", "共同所有者");
        assertThat(view.currentMemberRole()).isNull();
    }

    private Role role(Long id, String code, RoleScope scope, Long groupId, int permissionLevel) {
        return Role.builder()
            .id(id)
            .roleCode(code)
            .roleName(code)
            .roleScope(scope)
            .roleGroupId(groupId)
            .permissionLevel(permissionLevel)
            .status(1)
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
