package com.company.idm.application.rbac;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rolegroup.DelegatedPermissionPairPolicy;
import com.company.idm.application.user.SystemAdministratorProtectionPolicy;
import com.company.idm.common.enums.PermissionType;
import com.company.idm.common.exception.BizException;
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

class RbacApplicationServiceRoleScopeTest {

    @Test
    void groupRolesCannotBindPlatformPermissions() {
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
        Role groupRole = Role.builder()
            .id(30L)
            .roleCode("PROJECT_REVIEWER")
            .roleName("项目观察员")
            .roleScope(RoleScope.GROUP)
            .roleGroupId(10L)
            .permissionLevel(999)
            .status(1)
            .build();
        Permission permission = Permission.builder()
            .id(4L)
            .permissionCode("USER_READ")
            .permissionName("用户查询")
            .permissionType(PermissionType.API)
            .resourcePath("/api/v1/users")
            .action("GET")
            .status(1)
            .build();
        when(roleRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(groupRole));
        when(roleRepository.findPermissionIdsByRoleId(30L)).thenReturn(List.of());
        when(permissionRepository.findAll()).thenReturn(List.of(permission));

        assertThatThrownBy(() -> service.grantPermissions(
            new GrantRolePermissionsCommand(30L, List.of(4L), List.of(), "admin")
        ))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("GROUP_ROLE_PERMISSION_FORBIDDEN");

        verify(roleRepository, never()).assignPermissions(30L, List.of(4L));
    }
}
