package com.company.idm.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rolegroup.DelegatedPermissionPairPolicy;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.MenuRepository;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RbacApplicationServiceRoleProtectionTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final PermissionLevelRuleService permissionLevelRuleService = mock(PermissionLevelRuleService.class);
    private final RbacApplicationService service = new RbacApplicationService(
        roleRepository,
        mock(MenuRepository.class),
        permissionRepository,
        mock(UserRepository.class),
        mock(AuditLogRepository.class),
        mock(PolicyRefreshService.class),
        permissionLevelRuleService,
        mock(MenuVisibilityPermissionService.class),
        new DelegatedPermissionPairPolicy()
    );

    @Test
    void rejectsBuiltInRolePermissionLevelChanges() {
        Role role = builtInRole();
        when(roleRepository.findById(8L)).thenReturn(Optional.of(role));

        assertCode(
            () -> service.updateRole(new UpdateRoleCommand(8L, "普通用户", 30, "更新备注"), "admin"),
            "BUILT_IN_ROLE_PERMISSION_LEVEL_LOCKED"
        );

        verify(roleRepository, never()).save(any());
    }

    @Test
    void allowsBuiltInRoleNameAndRemarkChangesWhenPermissionLevelIsUnchanged() {
        Role role = builtInRole();
        when(roleRepository.findById(8L)).thenReturn(Optional.of(role));
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
        when(roleRepository.findById(8L)).thenReturn(Optional.of(builtInRole()));

        assertCode(
            () -> service.updateRoleStatus(new UpdateRoleStatusCommand(8L, 0, "admin")),
            "BUILT_IN_ROLE_STATUS_LOCKED"
        );

        verify(roleRepository, never()).updateStatus(any(), any());
    }

    @Test
    void rejectsSingleAndBatchDeletionOfBuiltInRoles() {
        when(roleRepository.findById(8L)).thenReturn(Optional.of(builtInRole()));

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
        when(roleRepository.findById(9L)).thenReturn(Optional.of(role));

        service.updateRoleStatus(new UpdateRoleStatusCommand(9L, 0, "admin"));

        verify(permissionLevelRuleService).checkCanUpdateRole("admin", role, 30);
        verify(roleRepository).updateStatus(9L, 0);
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
