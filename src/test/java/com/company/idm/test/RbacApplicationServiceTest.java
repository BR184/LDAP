package com.company.idm.test;

import com.company.idm.application.rbac.BindRoleMenusCommand;
import com.company.idm.application.rbac.CreateRoleCommand;
import com.company.idm.application.rbac.DeleteRoleCommand;
import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.rbac.UpdateRoleCommand;
import com.company.idm.common.enums.MenuType;
import com.company.idm.common.enums.PermissionType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.Menu;
import com.company.idm.domain.rbac.MenuRepository;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private MenuRepository menuRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PolicyRefreshService policyRefreshService;

    @Mock
    private PermissionLevelRuleService permissionLevelRuleService;

    @InjectMocks
    private RbacApplicationService rbacApplicationService;

    @Test
    void shouldListRolesPermissionsAndMenus() {
        when(roleRepository.findAll()).thenReturn(List.of(Role.builder()
            .id(1L).roleCode("ADMIN").roleName("管理员").permissionLevel(1).builtIn(1).status(1).build()));
        when(permissionRepository.findAll()).thenReturn(List.of(Permission.builder()
            .id(1L).permissionCode("USER_READ").permissionName("用户查询").permissionType(PermissionType.API).build()));
        when(menuRepository.findAllEnabled()).thenReturn(List.of(Menu.builder()
            .id(1L).menuCode("UINIT0").menuName("uinit0").menuType(MenuType.CATALOG).parentId(0L).sortNo(1).status(1).visible(1).build()));

        assertThat(rbacApplicationService.listRoles()).hasSize(1);
        assertThat(rbacApplicationService.listPermissions()).hasSize(1);
        assertThat(rbacApplicationService.listAllMenus()).hasSize(1);
    }

    @Test
    void shouldCreateRoleSuccessfully() {
        when(roleRepository.findByCode("DEV_ENGINEER")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenReturn(Role.builder()
            .id(2L).roleCode("DEV_ENGINEER").roleName("开发工程师").permissionLevel(3).builtIn(0).status(1).remark("test").build());

        Role role = rbacApplicationService.createRole(new CreateRoleCommand("DEV_ENGINEER", "开发工程师", 3, "test"), "admin");

        assertThat(role.getRoleCode()).isEqualTo("DEV_ENGINEER");
        assertThat(role.getPermissionLevel()).isEqualTo(3);
        verify(permissionLevelRuleService).checkCanCreateRole("admin", 3);
        verify(auditLogRepository).save(any());
    }

    @Test
    void shouldRejectDuplicateRoleCode() {
        when(roleRepository.findByCode("ADMIN"))
            .thenReturn(Optional.of(Role.builder().id(1L).roleCode("ADMIN").roleName("管理员").permissionLevel(1).builtIn(1).status(1).build()));

        assertThatThrownBy(() -> rbacApplicationService.createRole(new CreateRoleCommand("ADMIN", "重复角色", 1, null), "admin"))
            .isInstanceOf(BizException.class)
            .hasMessage("角色编码已存在");
    }

    @Test
    void shouldUpdateRoleSuccessfully() {
        when(roleRepository.findById(2L)).thenReturn(Optional.of(Role.builder()
            .id(2L).roleCode("DEV_ENGINEER").roleName("开发工程师").permissionLevel(3).builtIn(0).status(1).remark("old").build()));
        when(roleRepository.save(any(Role.class))).thenReturn(Role.builder()
            .id(2L).roleCode("DEV_ENGINEER").roleName("开发工程师-更新").permissionLevel(4).builtIn(0).status(1).remark("new").build());

        Role role = rbacApplicationService.updateRole(new UpdateRoleCommand(2L, "开发工程师-更新", 4, "new"), "admin");

        assertThat(role.getRoleName()).isEqualTo("开发工程师-更新");
        assertThat(role.getPermissionLevel()).isEqualTo(4);
    }

    @Test
    void shouldDeleteRoleSuccessfully() {
        Role role = Role.builder().id(2L).roleCode("DEV_ENGINEER").roleName("开发工程师").permissionLevel(3).builtIn(0).status(1).build();
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(roleRepository.existsUserBinding(2L)).thenReturn(false);

        rbacApplicationService.deleteRole(new DeleteRoleCommand(2L, "admin"));

        verify(roleRepository).delete(2L);
        verify(policyRefreshService).refresh();
    }

    @Test
    void shouldBindMenusSuccessfully() {
        Role role = Role.builder().id(1L).roleCode("ADMIN").roleName("管理员").permissionLevel(1).builtIn(1).status(1).build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(menuRepository.findByIds(List.of(1L, 2L))).thenReturn(List.of(
            Menu.builder().id(1L).menuCode("UINIT0").menuName("uinit0").menuType(MenuType.CATALOG).parentId(0L).sortNo(1).status(1).visible(1).minPermissionLevel(1).build(),
            Menu.builder().id(2L).menuCode("SYSTEM_MANAGEMENT").menuName("系统管理").menuType(MenuType.CATALOG).parentId(1L).sortNo(2).status(1).visible(1).minPermissionLevel(1).build()
        ));

        rbacApplicationService.bindMenus(new BindRoleMenusCommand(1L, List.of(1L, 2L), "admin"));

        verify(permissionLevelRuleService).checkCanBindMenus(eq("admin"), eq(role), any());
        verify(roleRepository).bindMenus(1L, List.of(1L, 2L));
    }

    @Test
    void shouldAssignUserRolesSuccessfully() {
        User user = User.builder().id(2L).username("zhangsan").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(roleRepository.findByIds(List.of(2L))).thenReturn(List.of(Role.builder()
            .id(2L).roleCode("NORMAL_USER").roleName("普通用户").permissionLevel(3).builtIn(1).status(1).build()));

        rbacApplicationService.assignUserRoles(new com.company.idm.application.rbac.AssignUserRolesCommand(2L, List.of(2L), "admin"));

        verify(userRepository).assignRoles(2L, List.of(2L));
        verify(policyRefreshService).refresh();
    }

    @Test
    void shouldReturnCurrentUserMenus() {
        when(userRepository.findRoleCodesByUsername("admin")).thenReturn(Set.of("ADMIN"));
        when(menuRepository.findByRoleCodes(Set.of("ADMIN"))).thenReturn(List.of(Menu.builder()
            .id(1L).menuCode("UINIT0").menuName("uinit0").menuType(MenuType.CATALOG).parentId(0L).sortNo(1).status(1).visible(1).build()));

        assertThat(rbacApplicationService.listCurrentUserMenus("admin")).hasSize(1);
    }
}
