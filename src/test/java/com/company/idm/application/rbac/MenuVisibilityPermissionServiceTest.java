package com.company.idm.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.common.enums.MenuType;
import com.company.idm.domain.rbac.Menu;
import com.company.idm.domain.rbac.MenuRepository;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MenuVisibilityPermissionServiceTest {

    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final MenuRepository menuRepository = mock(MenuRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final MenuVisibilityPermissionService service = new MenuVisibilityPermissionService(
        permissionRepository,
        menuRepository,
        roleRepository
    );

    @Test
    void createsVisibilityPermissionForPageMenuAndGrantsFullAccessRoles() {
        Menu menu = pageMenu(101L, "PROJECT_MANAGEMENT", "项目管理");
        Permission savedPermission = Permission.builder().id(501L).build();
        when(menuRepository.findVisibilityPermissionId(menu.getId())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenReturn(savedPermission);
        when(roleRepository.findAll()).thenReturn(List.of(
            role(1L, 1),
            role(2L, 2),
            role(3L, 3)
        ));

        service.synchronize(menu);

        ArgumentCaptor<Permission> permissionCaptor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository).save(permissionCaptor.capture());
        Permission permission = permissionCaptor.getValue();
        assertThat(permission.getPermissionCode()).isEqualTo("MENU_VIEW_PROJECT_MANAGEMENT");
        assertThat(permission.getPermissionName()).isEqualTo("菜单显示_项目管理");
        assertThat(permission.getResourcePath()).isEqualTo("menu:PROJECT_MANAGEMENT");
        assertThat(permission.getAction()).isEqualTo("VIEW");
        verify(menuRepository).bindVisibilityPermission(101L, 501L);
        verify(roleRepository).grantPermissionToRoles(List.of(1L, 2L), 501L);
    }

    @Test
    void removesVisibilityPermissionWhenDeletingPageMenu() {
        Menu menu = pageMenu(101L, "PROJECT_MANAGEMENT", "项目管理");
        when(menuRepository.findVisibilityPermissionId(menu.getId())).thenReturn(Optional.of(501L));

        service.remove(menu);

        verify(menuRepository).removeVisibilityPermission(101L);
        verify(roleRepository).removePermissionFromAllRoles(501L);
        verify(permissionRepository).delete(501L);
    }

    @Test
    void catalogDoesNotCreateVisibilityPermission() {
        Menu catalog = Menu.builder()
            .id(100L)
            .menuCode("PROJECTS")
            .menuName("项目")
            .menuType(MenuType.CATALOG)
            .build();
        when(menuRepository.findVisibilityPermissionId(catalog.getId())).thenReturn(Optional.empty());

        service.synchronize(catalog);

        verify(menuRepository).findVisibilityPermissionId(100L);
    }

    private Menu pageMenu(Long id, String menuCode, String menuName) {
        return Menu.builder()
            .id(id)
            .menuCode(menuCode)
            .menuName(menuName)
            .menuType(MenuType.MENU)
            .sortNo(10)
            .build();
    }

    private Role role(Long id, int permissionLevel) {
        return Role.builder()
            .id(id)
            .status(1)
            .permissionLevel(permissionLevel)
            .build();
    }
}
