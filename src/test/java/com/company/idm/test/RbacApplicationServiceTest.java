package com.company.idm.test;

import com.company.idm.application.rbac.BindRoleMenusCommand;
import com.company.idm.application.rbac.CreateRoleCommand;
import com.company.idm.application.rbac.CreateMenuCommand;
import com.company.idm.application.rbac.DeleteRoleCommand;
import com.company.idm.application.rbac.DeleteMenuCommand;
import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.rbac.UpdateRoleCommand;
import com.company.idm.application.rbac.UpdateMenuCommand;
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
        Menu rootMenu = Menu.builder()
            .id(1L).menuCode("UINIT0").menuName("uinit0").menuType(MenuType.CATALOG).parentId(0L)
            .sortNo(1).status(1).visible(1).minPermissionLevel(1).build();
        Menu systemMenu = Menu.builder()
            .id(2L).menuCode("SYSTEM_MANAGEMENT").menuName("系统管理").menuType(MenuType.CATALOG).parentId(1L)
            .sortNo(2).status(1).visible(1).minPermissionLevel(1).build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(menuRepository.findByIds(List.of(1L, 2L))).thenReturn(List.of(rootMenu, systemMenu));
        when(menuRepository.findById(1L)).thenReturn(Optional.of(rootMenu));

        rbacApplicationService.bindMenus(new BindRoleMenusCommand(1L, List.of(1L, 2L), "admin"));

        verify(permissionLevelRuleService).checkCanBindMenus(eq("admin"), eq(role), any());
        verify(roleRepository).bindMenus(1L, List.of(1L, 2L));
    }

    @Test
    void shouldBindMenusWithAncestorsAutomatically() {
        Role role = Role.builder().id(2L).roleCode("DEV").roleName("开发").permissionLevel(3).builtIn(0).status(1).build();
        Menu rootMenu = Menu.builder()
            .id(1L).menuCode("UINIT0").menuName("uinit0").menuType(MenuType.CATALOG).parentId(0L)
            .sortNo(1).status(1).visible(1).minPermissionLevel(1).build();
        Menu systemMenu = Menu.builder()
            .id(6L).menuCode("SYSTEM_MANAGEMENT").menuName("系统管理").menuType(MenuType.CATALOG).parentId(1L)
            .sortNo(2).status(1).visible(1).minPermissionLevel(1).build();
        Menu menuManagement = Menu.builder()
            .id(8L).menuCode("MENU_MANAGEMENT").menuName("菜单管理").menuType(MenuType.MENU).parentId(6L)
            .path("/system/menus").component("system/menu/index")
            .sortNo(2).status(1).visible(1).minPermissionLevel(1).build();
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(menuRepository.findByIds(List.of(8L))).thenReturn(List.of(menuManagement));
        when(menuRepository.findById(6L)).thenReturn(Optional.of(systemMenu));
        when(menuRepository.findById(1L)).thenReturn(Optional.of(rootMenu));

        rbacApplicationService.bindMenus(new BindRoleMenusCommand(2L, List.of(8L), "admin"));

        verify(roleRepository).bindMenus(2L, List.of(1L, 6L, 8L));
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

    @Test
    void shouldCreateMenuSuccessfully() {
        Menu parent = Menu.builder()
            .id(6L).menuCode("SYSTEM_MANAGEMENT").menuName("系统管理").menuType(MenuType.CATALOG).parentId(1L)
            .path("/system").component("Layout").sortNo(2).status(1).visible(1).minPermissionLevel(1).build();
        when(menuRepository.findByCode("DATA_SYNC")).thenReturn(Optional.empty());
        when(menuRepository.findById(6L)).thenReturn(Optional.of(parent));
        when(menuRepository.save(any(Menu.class))).thenReturn(Menu.builder()
            .id(20L).menuCode("DATA_SYNC").menuName("数据同步").menuType(MenuType.MENU).parentId(6L)
            .path("/system/data-sync").component("system/data-sync/index").icon("sync")
            .sortNo(4).status(1).visible(1).minPermissionLevel(1).remark("同步菜单").build());
        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(Role.builder()
            .id(1L).roleCode("ADMIN").roleName("管理员").permissionLevel(1).builtIn(1).status(1).build()));

        Menu menu = rbacApplicationService.createMenu(new CreateMenuCommand(
            "DATA_SYNC", "数据同步", 6L, MenuType.MENU, "/system/data-sync", "system/data-sync/index", "sync", 4, 1, "同步菜单"
        ), "admin");

        assertThat(menu.getId()).isEqualTo(20L);
        verify(permissionLevelRuleService).checkCanManageMenu("admin");
        verify(menuRepository).bindRole(1L, 20L);
    }

    @Test
    void shouldUpdateMenuSuccessfully() {
        Menu existing = Menu.builder()
            .id(8L).menuCode("MENU_MANAGEMENT").menuName("菜单管理").menuType(MenuType.MENU).parentId(6L)
            .path("/system/menus").component("system/menu/index").icon("menu")
            .sortNo(2).status(1).visible(1).minPermissionLevel(1).remark("旧备注").build();
        Menu parent = Menu.builder()
            .id(6L).menuCode("SYSTEM_MANAGEMENT").menuName("系统管理").menuType(MenuType.CATALOG).parentId(1L)
            .path("/system").component("Layout").sortNo(2).status(1).visible(1).minPermissionLevel(1).build();
        when(menuRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(menuRepository.findById(6L)).thenReturn(Optional.of(parent));
        when(menuRepository.existsChildren(8L)).thenReturn(false);
        when(menuRepository.existsRoleBindingConflict(8L, 1)).thenReturn(false);
        when(menuRepository.save(any(Menu.class))).thenReturn(Menu.builder()
            .id(8L).menuCode("MENU_MANAGEMENT").menuName("菜单管理-更新").menuType(MenuType.MENU).parentId(6L)
            .path("/system/menu-center").component("system/menu/center").icon("menu")
            .sortNo(5).status(1).visible(1).minPermissionLevel(1).remark("新备注").build());

        Menu menu = rbacApplicationService.updateMenu(new UpdateMenuCommand(
            8L, "菜单管理-更新", 6L, MenuType.MENU, "/system/menu-center", "system/menu/center", "menu", 5, 1, "新备注"
        ), "admin");

        assertThat(menu.getMenuName()).isEqualTo("菜单管理-更新");
        assertThat(menu.getPath()).isEqualTo("/system/menu-center");
        verify(permissionLevelRuleService).checkCanManageMenu("admin");
    }

    @Test
    void shouldDeleteMenuSuccessfully() {
        Menu existing = Menu.builder()
            .id(8L).menuCode("MENU_MANAGEMENT").menuName("菜单管理").menuType(MenuType.MENU).parentId(6L)
            .path("/system/menus").component("system/menu/index").sortNo(2).status(1).visible(1).build();
        when(menuRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(menuRepository.existsChildren(8L)).thenReturn(false);

        rbacApplicationService.deleteMenu(new DeleteMenuCommand(8L, "admin"));

        verify(permissionLevelRuleService).checkCanManageMenu("admin");
        verify(menuRepository).removeRoleBindings(8L);
        verify(menuRepository).delete(8L);
    }
}
