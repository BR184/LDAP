package com.company.idm.application.rbac;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rolegroup.DelegatedPermissionPairPolicy;
import com.company.idm.application.user.SystemAdministratorProtectionPolicy;
import com.company.idm.common.enums.PermissionType;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.MenuRepository;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RbacApplicationServiceRoleGroupPermissionTest {

    @Test
    void roleGroupReadAccessKeepsTheRoleGroupMenuVisible() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        PermissionRepository permissionRepository = mock(PermissionRepository.class);
        RbacApplicationService service = new RbacApplicationService(
            roleRepository,
            mock(MenuRepository.class),
            permissionRepository,
            mock(UserRepository.class),
            mock(AuditLogRepository.class),
            mock(PolicyRefreshService.class),
            mock(PermissionLevelRuleService.class),
            mock(MenuVisibilityPermissionService.class),
            new DelegatedPermissionPairPolicy(),
            new SystemAdministratorProtectionPolicy()
        );
        Role role = Role.builder()
            .id(30L)
            .roleCode("ROLE_GROUP_READER")
            .roleName("角色组查询员")
            .roleScope(RoleScope.SYSTEM)
            .permissionLevel(999)
            .status(1)
            .build();
        Permission readPermission = permission(
            4L,
            DelegatedPermissionPairPolicy.ROLE_GROUP_READ,
            PermissionType.API
        );
        Permission menuPermission = permission(
            5L,
            DelegatedPermissionPairPolicy.ROLE_GROUP_MENU_VIEW,
            PermissionType.MENU
        );
        when(roleRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(role));
        when(roleRepository.findPermissionIdsByRoleId(30L)).thenReturn(List.of());
        when(permissionRepository.findAll()).thenReturn(List.of(readPermission, menuPermission));

        service.grantPermissions(new GrantRolePermissionsCommand(30L, List.of(4L), List.of(), "admin"));

        verify(roleRepository).assignPermissions(30L, List.of(4L, 5L));
    }

    private Permission permission(Long id, String code, PermissionType type) {
        return Permission.builder()
            .id(id)
            .permissionCode(code)
            .permissionName(code)
            .permissionType(type)
            .resourcePath("/api/v1/role-groups")
            .action(type == PermissionType.MENU ? "VIEW" : "GET")
            .status(1)
            .build();
    }
}
