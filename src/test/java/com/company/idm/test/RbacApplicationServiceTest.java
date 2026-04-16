package com.company.idm.test;

import com.company.idm.application.rbac.CreateRoleCommand;
import com.company.idm.application.rbac.GrantRolePermissionsCommand;
import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.common.enums.PermissionType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证角色权限应用服务的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RbacApplicationServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PolicyRefreshService policyRefreshService;

    @InjectMocks
    private RbacApplicationService rbacApplicationService;

    @Test
    void shouldListRolesAndPermissions() {
        when(roleRepository.findAll()).thenReturn(List.of(Role.builder().id(1L).roleCode("SUPER_ADMIN").roleName("超级管理员").status(1).build()));
        when(permissionRepository.findAll()).thenReturn(List.of(Permission.builder()
            .id(1L).permissionCode("USER_READ").permissionName("用户查询").permissionType(PermissionType.API).build()));

        assertThat(rbacApplicationService.listRoles()).hasSize(1);
        assertThat(rbacApplicationService.listPermissions()).hasSize(1);
    }

    @Test
    void shouldCreateRoleSuccessfully() {
        when(roleRepository.findByCode("OPS_ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.save(org.mockito.ArgumentMatchers.any(Role.class)))
            .thenReturn(Role.builder().id(2L).roleCode("OPS_ADMIN").roleName("运维管理员").status(1).remark("test").build());

        Role role = rbacApplicationService.createRole(new CreateRoleCommand("OPS_ADMIN", "运维管理员", "test"), "admin");

        assertThat(role.getRoleCode()).isEqualTo("OPS_ADMIN");
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectDuplicateRoleCode() {
        when(roleRepository.findByCode("SUPER_ADMIN"))
            .thenReturn(Optional.of(Role.builder().id(1L).roleCode("SUPER_ADMIN").roleName("超级管理员").status(1).build()));

        assertThatThrownBy(() -> rbacApplicationService.createRole(new CreateRoleCommand("SUPER_ADMIN", "重复角色", null), "admin"))
            .isInstanceOf(BizException.class)
            .hasMessage("角色编码已存在");
    }

    @Test
    void shouldGrantPermissionsSuccessfully() {
        when(roleRepository.findById(1L))
            .thenReturn(Optional.of(Role.builder().id(1L).roleCode("SUPER_ADMIN").roleName("超级管理员").status(1).build()));

        rbacApplicationService.grantPermissions(new GrantRolePermissionsCommand(1L, List.of(1L, 2L), "admin"));

        verify(roleRepository).assignPermissions(1L, List.of(1L, 2L));
        verify(policyRefreshService).refresh();
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.any());
    }
}

