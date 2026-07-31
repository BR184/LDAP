package com.company.idm.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.Menu;
import com.company.idm.domain.rbac.MenuRepository;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RbacApplicationServiceMenuVisibilityTest {

    @Test
    void includesUserManagementWhenUserReadPermissionGrantsIt() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        MenuRepository menuRepository = mock(MenuRepository.class);
        PermissionRepository permissionRepository = mock(PermissionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        RbacApplicationService service = new RbacApplicationService(
            roleRepository,
            menuRepository,
            permissionRepository,
            userRepository,
            mock(AuditLogRepository.class),
            mock(PolicyRefreshService.class),
            mock(PermissionLevelRuleService.class)
        );

        Menu root = menu(1L, 0L, "UINIT0");
        Menu personnel = menu(2L, 1L, "PERSONNEL_MANAGEMENT");
        Menu userManagement = menu(3L, 2L, "USER_MANAGEMENT");
        Set<String> roleCodes = Set.of("DIRECT_MANAGER");
        Set<String> permissionCodes = Set.of("USER_READ");
        when(userRepository.findRoleCodesByUserId("manager")).thenReturn(roleCodes);
        when(permissionRepository.findPermissionCodesByUserId("manager")).thenReturn(permissionCodes);
        when(menuRepository.findByAccess(roleCodes, permissionCodes)).thenReturn(List.of(userManagement));
        when(menuRepository.findAllEnabled()).thenReturn(List.of(root, personnel, userManagement));

        List<Menu> menus = service.listCurrentUserMenus("manager");

        assertThat(menus).extracting(Menu::getMenuCode)
            .containsExactly("UINIT0", "PERSONNEL_MANAGEMENT", "USER_MANAGEMENT");
        verify(menuRepository).findByAccess(roleCodes, permissionCodes);
    }

    private Menu menu(Long id, Long parentId, String menuCode) {
        return Menu.builder()
            .id(id)
            .parentId(parentId)
            .menuCode(menuCode)
            .status(1)
            .visible(1)
            .build();
    }
}
