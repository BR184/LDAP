package com.company.idm.application.rbac;

import com.company.idm.common.enums.MenuType;
import com.company.idm.common.enums.PermissionType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Menu;
import com.company.idm.domain.rbac.MenuRepository;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MenuVisibilityPermissionService {

    private static final String MENU_VIEW_PREFIX = "MENU_VIEW_";
    private static final String MENU_RESOURCE_PREFIX = "menu:";
    private static final String MENU_PERMISSION_NAME_PREFIX = "菜单显示_";
    private static final String VIEW_ACTION = "VIEW";
    private static final int FULL_ACCESS_PERMISSION_LEVEL = 2;

    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;

    public void synchronize(Menu menu) {
        validatePersistedMenu(menu);
        if (menu.getMenuType() != MenuType.MENU) {
            remove(menu);
            return;
        }

        Optional<Long> existingPermissionId = menuRepository.findVisibilityPermissionId(menu.getId());
        Optional<Permission> existingPermission = existingPermissionId.flatMap(permissionRepository::findById);
        if (existingPermissionId.isPresent() && existingPermission.isEmpty()) {
            menuRepository.removeVisibilityPermission(menu.getId());
        }
        Permission permission = existingPermission.orElseGet(() -> Permission.builder()
                .permissionCode(permissionCode(menu.getMenuCode()))
                .permissionType(PermissionType.MENU)
                .parentId(0L)
                .status(1)
                .build());

        Permission savedPermission = permissionRepository.save(Permission.builder()
            .id(permission.getId())
            .permissionCode(permissionCode(menu.getMenuCode()))
            .permissionName(MENU_PERMISSION_NAME_PREFIX + menu.getMenuName())
            .permissionType(PermissionType.MENU)
            .resourcePath(MENU_RESOURCE_PREFIX + menu.getMenuCode())
            .action(VIEW_ACTION)
            .parentId(0L)
            .sortNo(menu.getSortNo() == null ? 0 : menu.getSortNo())
            .status(1)
            .remark(menu.getMenuName() + "页面显示权限")
            .build());

        if (existingPermission.isEmpty()) {
            menuRepository.bindVisibilityPermission(menu.getId(), savedPermission.getId());
            grantToFullAccessRoles(savedPermission.getId());
        }
    }

    public void remove(Menu menu) {
        validatePersistedMenu(menu);
        menuRepository.findVisibilityPermissionId(menu.getId()).ifPresent(permissionId -> {
            menuRepository.removeVisibilityPermission(menu.getId());
            roleRepository.removePermissionFromAllRoles(permissionId);
            permissionRepository.delete(permissionId);
        });
    }

    static String permissionCode(String menuCode) {
        return MENU_VIEW_PREFIX + menuCode;
    }

    private void grantToFullAccessRoles(Long permissionId) {
        List<Long> roleIds = roleRepository.findAll().stream()
            .filter(role -> role.getStatus() != null && role.getStatus() == 1)
            .filter(role -> role.getPermissionLevel() != null
                && role.getPermissionLevel() <= FULL_ACCESS_PERMISSION_LEVEL)
            .map(Role::getId)
            .toList();
        roleRepository.grantPermissionToRoles(roleIds, permissionId);
    }

    private void validatePersistedMenu(Menu menu) {
        if (menu == null || menu.getId() == null || menu.getMenuCode() == null || menu.getMenuCode().isBlank()) {
            throw new BizException("MENU_INVALID", "菜单数据不完整，无法维护菜单显示权限");
        }
    }
}
