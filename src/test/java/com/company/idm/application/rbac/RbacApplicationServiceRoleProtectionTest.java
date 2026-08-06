package com.company.idm.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rolegroup.DelegatedPermissionPairPolicy;
import com.company.idm.application.user.SystemAdministratorProtectionPolicy;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.MenuRepository;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RbacApplicationServiceRoleProtectionTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final PermissionLevelRuleService permissionLevelRuleService = mock(PermissionLevelRuleService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RbacApplicationService service = new RbacApplicationService(
        roleRepository,
        mock(MenuRepository.class),
        permissionRepository,
        userRepository,
        mock(AuditLogRepository.class),
        mock(PolicyRefreshService.class),
        permissionLevelRuleService,
        mock(MenuVisibilityPermissionService.class),
        new DelegatedPermissionPairPolicy(),
        new SystemAdministratorProtectionPolicy()
    );

    @Test
    void rejectsBuiltInRolePermissionLevelChanges() {
        Role role = builtInRole();
        when(roleRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(role));

        assertCode(
            () -> service.updateRole(new UpdateRoleCommand(8L, "普通用户", 30, "更新备注"), "admin"),
            "BUILT_IN_ROLE_PERMISSION_LEVEL_LOCKED"
        );

        verify(roleRepository, never()).save(any());
    }

    @Test
    void allowsBuiltInRoleNameAndRemarkChangesWhenPermissionLevelIsUnchanged() {
        Role role = builtInRole();
        when(roleRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(role));
        when(roleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(permissionRepository.findAll()).thenReturn(List.of());

        Role updated = service.updateRole(
            new UpdateRoleCommand(8L, "企业普通用户", role.getPermissionLevel(), "企业基础角色"),
            "admin"
        );

        assertThat(updated.getRoleName()).isEqualTo("企业普通用户");
        assertThat(updated.getRemark()).isEqualTo("企业基础角色");
        assertThat(updated.getPermissionLevel()).isEqualTo(role.getPermissionLevel());
    }

    @Test
    void rejectsBuiltInRoleStatusChanges() {
        when(roleRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(builtInRole()));

        assertCode(
            () -> service.updateRoleStatus(new UpdateRoleStatusCommand(8L, 0, "admin")),
            "BUILT_IN_ROLE_STATUS_LOCKED"
        );

        verify(roleRepository, never()).updateStatus(any(), any());
    }

    @Test
    void rejectsSingleAndBatchDeletionOfBuiltInRoles() {
        when(roleRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(builtInRole()));

        assertCode(
            () -> service.deleteRole(new DeleteRoleCommand(8L, "admin")),
            "BUILT_IN_ROLE_DELETE_FORBIDDEN"
        );
        assertCode(
            () -> service.batchDeleteRoles(new BatchDeleteRolesCommand(List.of(8L), "admin")),
            "BUILT_IN_ROLE_DELETE_FORBIDDEN"
        );

        verify(roleRepository, never()).delete(8L);
    }

    @Test
    void customRolesRetainExistingStatusBehavior() {
        Role role = Role.builder()
            .id(9L)
            .roleCode("AUDITOR")
            .permissionLevel(30)
            .builtIn(0)
            .status(1)
            .roleScope(RoleScope.SYSTEM)
            .build();
        when(roleRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(role));

        service.updateRoleStatus(new UpdateRoleStatusCommand(9L, 0, "admin"));

        verify(permissionLevelRuleService).checkCanUpdateRole("admin", role, 30);
        verify(roleRepository).updateStatus(9L, 0);
    }

    @Test
    void rejectsRoleAssignmentWhenTheClientSnapshotIsStale() {
        User user = User.builder().id(21L).userId("employee").roleCodes(Set.of("NORMAL_USER")).build();
        when(userRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(user));
        when(userRepository.findRoleIdsByUserId(21L)).thenReturn(List.of(8L, 9L));

        assertCode(
            () -> service.assignUserRoles(new AssignUserRolesCommand(
                21L,
                List.of(8L, 10L),
                List.of(8L),
                "admin"
            )),
            "USER_ROLE_ASSIGN_CONFLICT"
        );

        verify(roleRepository, never()).findByIds(any());
        verify(userRepository, never()).assignRoles(any(), any(), any());
    }

    @Test
    void rejectsRemovingSuperAdminFromTheBuiltInAdminAccount() {
        User admin = User.builder().id(1L).userId("admin").roleCodes(Set.of("SUPER_ADMIN")).build();
        Role customRole = Role.builder()
            .id(12L)
            .roleCode("PLATFORM_A_ADMIN")
            .permissionLevel(10)
            .status(1)
            .build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(roleRepository.findByIds(List.of(12L))).thenReturn(List.of(customRole));

        assertCode(
            () -> service.assignUserRoles(new AssignUserRolesCommand(
                1L,
                List.of(12L),
                List.of(1L),
                "admin"
            )),
            "SYSTEM_ADMIN_SUPER_ADMIN_REQUIRED"
        );

        verify(permissionLevelRuleService, never()).checkCanAssignRoles(any(), any(), any());
        verify(userRepository, never()).assignRoles(any(), any(), any());
    }

    @Test
    void rejectsRolePermissionAssignmentWhenTheClientSnapshotIsStale() {
        Role role = Role.builder()
            .id(12L)
            .roleCode("PLATFORM_A_ADMIN")
            .permissionLevel(10)
            .status(1)
            .roleScope(RoleScope.SYSTEM)
            .build();
        when(roleRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(role));
        when(roleRepository.findPermissionIdsByRoleId(12L)).thenReturn(List.of(4L, 5L));

        assertCode(
            () -> service.grantPermissions(new GrantRolePermissionsCommand(
                12L,
                List.of(4L, 6L),
                List.of(4L),
                "admin"
            )),
            "ROLE_PERMISSION_ASSIGN_CONFLICT"
        );

        verify(permissionLevelRuleService, never()).checkCanUpdateRole(any(), any(), any());
        verify(roleRepository, never()).assignPermissions(any(), any());
    }

    @Test
    void rejectsManualPermissionChangesForSuperAdmin() {
        Role role = Role.builder()
            .id(1L)
            .roleCode("SUPER_ADMIN")
            .permissionLevel(1)
            .builtIn(1)
            .status(1)
            .roleScope(RoleScope.SYSTEM)
            .build();
        when(roleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(role));
        when(roleRepository.findPermissionIdsByRoleId(1L)).thenReturn(List.of(4L, 5L));

        assertCode(
            () -> service.grantPermissions(new GrantRolePermissionsCommand(
                1L,
                List.of(4L),
                List.of(4L, 5L),
                "admin"
            )),
            "SUPER_ADMIN_PERMISSIONS_SYSTEM_MANAGED"
        );

        verify(roleRepository, never()).assignPermissions(any(), any());
    }

    private Role builtInRole() {
        return Role.builder()
            .id(8L)
            .roleCode("NORMAL_USER")
            .roleName("普通用户")
            .permissionLevel(10)
            .builtIn(1)
            .status(1)
            .remark("系统内置普通用户角色")
            .roleScope(RoleScope.GLOBAL)
            .build();
    }

    private void assertCode(Runnable operation, String expectedCode) {
        assertThatThrownBy(operation::run)
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo(expectedCode);
    }
}
