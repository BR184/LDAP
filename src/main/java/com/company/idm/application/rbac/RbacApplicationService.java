package com.company.idm.application.rbac;

import com.company.idm.common.enums.MenuType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RbacApplicationService {

    private static final String AUTH_ME_PERMISSION_CODE = "AUTH_ME";
    private static final int FULL_ACCESS_PERMISSION_LEVEL = 2;

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PolicyRefreshService policyRefreshService;
    private final PermissionLevelRuleService permissionLevelRuleService;

    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    public Role getRole(Long roleId) {
        return roleRepository.findById(roleId)
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
    }

    public List<Long> listRoleMenuIds(Long roleId) {
        ensureRoleExists(roleId);
        return roleRepository.findMenuIdsByRoleId(roleId);
    }

    public List<Long> listRolePermissionIds(Long roleId) {
        ensureRoleExists(roleId);
        return roleRepository.findPermissionIdsByRoleId(roleId);
    }

    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    public List<Menu> listAllMenus() {
        return menuRepository.findAllEnabled();
    }

    public Menu getMenu(Long menuId) {
        return menuRepository.findById(menuId)
            .orElseThrow(() -> new BizException("MENU_NOT_FOUND", "菜单不存在"));
    }

    public List<Menu> listCurrentUserMenus(String username) {
        Set<String> roleCodes = userRepository.findRoleCodesByUsername(username);
        return menuRepository.findByRoleCodes(roleCodes);
    }

    @Transactional
    public Role createRole(CreateRoleCommand command, String operator) {
        permissionLevelRuleService.checkCanCreateRole(operator, command.permissionLevel());
        roleRepository.findByCode(command.roleCode())
            .ifPresent(role -> {
                throw new BizException("ROLE_CODE_DUPLICATE", "角色编码已存在");
            });
        Role role = roleRepository.save(Role.builder()
            .roleCode(command.roleCode())
            .roleName(command.roleName())
            .permissionLevel(command.permissionLevel())
            .builtIn(0)
            .remark(command.remark())
            .status(1)
            .build());
        applyDefaultAccessGrants(role, true);
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("ROLE_CREATE")
            .bizType("ROLE")
            .bizId(String.valueOf(role.getId()))
            .afterJson(role.getRoleCode())
            .result("SUCCESS")
            .build());
        return role;
    }

    @Transactional
    public Role updateRole(UpdateRoleCommand command, String operator) {
        Role role = roleRepository.findById(command.roleId())
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
        permissionLevelRuleService.checkCanUpdateRole(operator, role, command.permissionLevel());
        Role updated = roleRepository.save(Role.builder()
            .id(role.getId())
            .roleCode(role.getRoleCode())
            .roleName(command.roleName())
            .permissionLevel(command.permissionLevel())
            .builtIn(role.getBuiltIn())
            .status(role.getStatus())
            .remark(command.remark())
            .build());
        applyDefaultAccessGrants(updated, false);
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("ROLE_UPDATE")
            .bizType("ROLE")
            .bizId(String.valueOf(command.roleId()))
            .afterJson(updated.getRoleCode())
            .result("SUCCESS")
            .build());
        return updated;
    }

    @Transactional
    public void updateRoleStatus(UpdateRoleStatusCommand command) {
        Role role = roleRepository.findById(command.roleId())
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
        permissionLevelRuleService.checkCanUpdateRole(command.operator(), role, role.getPermissionLevel());
        roleRepository.updateStatus(command.roleId(), command.status());
        policyRefreshService.refresh();
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("ROLE_STATUS_CHANGE")
            .bizType("ROLE")
            .bizId(String.valueOf(command.roleId()))
            .afterJson(String.valueOf(command.status()))
            .result("SUCCESS")
            .build());
    }

    @Transactional
    public void deleteRole(DeleteRoleCommand command) {
        Role role = roleRepository.findById(command.roleId())
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
        validateRoleDeletion(role, command.operator());
        deleteRoleInternal(role, command.operator());
        policyRefreshService.refresh();
    }

    @Transactional
    public BatchDeleteRolesResult batchDeleteRoles(BatchDeleteRolesCommand command) {
        List<Long> uniqueRoleIds = command.roleIds().stream()
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                List::copyOf
            ));
        if (uniqueRoleIds.isEmpty()) {
            throw new BizException("ROLE_BATCH_DELETE_EMPTY", "待删除角色不能为空");
        }

        List<Role> roles = uniqueRoleIds.stream()
            .map(roleId -> roleRepository.findById(roleId)
                .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "存在待删除角色不存在")))
            .toList();

        for (Role role : roles) {
            validateRoleDeletion(role, command.operator());
        }

        for (Role role : roles) {
            deleteRoleInternal(role, command.operator());
        }
        policyRefreshService.refresh();
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("ROLE_BATCH_DELETE")
            .bizType("ROLE")
            .bizId(uniqueRoleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")))
            .afterJson("""
                {"totalCount":%s,"deletedCount":%s}
                """.formatted(uniqueRoleIds.size(), roles.size()))
            .result("SUCCESS")
            .build());
        return new BatchDeleteRolesResult(uniqueRoleIds.size(), roles.size());
    }

    @Transactional
    public void grantPermissions(GrantRolePermissionsCommand command) {
        roleRepository.findById(command.roleId())
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
        roleRepository.assignPermissions(command.roleId(), command.permissionIds());
        policyRefreshService.refresh();
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("ROLE_PERMISSION_GRANT")
            .bizType("ROLE")
            .bizId(String.valueOf(command.roleId()))
            .afterJson(String.valueOf(command.permissionIds()))
            .result("SUCCESS")
            .build());
    }

    @Transactional
    public void bindMenus(BindRoleMenusCommand command) {
        Role role = roleRepository.findById(command.roleId())
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
        List<Menu> menus = menuRepository.findByIds(command.menuIds());
        if (menus.size() != command.menuIds().size()) {
            throw new BizException("MENU_NOT_FOUND", "部分菜单不存在");
        }
        permissionLevelRuleService.checkCanBindMenus(command.operator(), role, menus);
        List<Long> normalizedMenuIds = expandMenuIdsWithAncestors(menus);
        roleRepository.bindMenus(command.roleId(), normalizedMenuIds);
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("ROLE_MENU_BIND")
            .bizType("ROLE")
            .bizId(String.valueOf(command.roleId()))
            .afterJson(String.valueOf(normalizedMenuIds))
            .result("SUCCESS")
            .build());
    }

    @Transactional
    public void assignUserRoles(AssignUserRolesCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        List<Role> roles = roleRepository.findByIds(command.roleIds());
        if (roles.size() != command.roleIds().size()) {
            throw new BizException("ROLE_NOT_FOUND", "部分角色不存在");
        }
        permissionLevelRuleService.checkCanAssignRoles(command.operator(), user, roles);
        userRepository.assignRoles(command.userId(), command.roleIds());
        policyRefreshService.refresh();
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_ROLE_ASSIGN")
            .bizType("USER")
            .bizId(String.valueOf(command.userId()))
            .afterJson(String.valueOf(command.roleIds()))
            .result("SUCCESS")
            .build());
    }

    @Transactional
    public Menu createMenu(CreateMenuCommand command, String operator) {
        permissionLevelRuleService.checkCanManageMenu(operator);
        menuRepository.findByCode(command.menuCode())
            .ifPresent(menu -> {
                throw new BizException("MENU_CODE_DUPLICATE", "菜单编码已存在");
            });
        Long parentId = normalizeParentId(command.parentId());
        validateMenuPayload(parentId, command.menuType(), command.path(), command.component(), null);
        Menu menu = menuRepository.save(Menu.builder()
            .menuCode(command.menuCode())
            .menuName(command.menuName())
            .parentId(parentId)
            .menuType(command.menuType())
            .path(command.path())
            .component(resolveComponent(command.menuType(), command.component()))
            .icon(command.icon())
            .sortNo(defaultSortNo(command.sortNo()))
            .status(1)
            .visible(1)
            .minPermissionLevel(command.minPermissionLevel())
            .remark(command.remark())
            .build());
        bindMenuToBuiltInManagers(menu);
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("MENU_CREATE")
            .bizType("MENU")
            .bizId(String.valueOf(menu.getId()))
            .afterJson(menu.getMenuCode())
            .result("SUCCESS")
            .build());
        return menu;
    }

    @Transactional
    public Menu updateMenu(UpdateMenuCommand command, String operator) {
        permissionLevelRuleService.checkCanManageMenu(operator);
        Menu existing = menuRepository.findById(command.menuId())
            .orElseThrow(() -> new BizException("MENU_NOT_FOUND", "菜单不存在"));
        Long parentId = normalizeParentId(command.parentId());
        validateMenuPayload(parentId, command.menuType(), command.path(), command.component(), existing.getId());
        if (command.menuType() == MenuType.MENU && menuRepository.existsChildren(existing.getId())) {
            throw new BizException("MENU_TYPE_INVALID", "存在子菜单时不允许将当前菜单调整为 MENU");
        }
        if (menuRepository.existsRoleBindingConflict(existing.getId(), command.minPermissionLevel())) {
            throw new BizException("MENU_PERMISSION_CONFLICT", "当前菜单已绑定更低权限角色，无法收紧可绑定等级");
        }
        Menu updated = menuRepository.save(Menu.builder()
            .id(existing.getId())
            .menuCode(existing.getMenuCode())
            .menuName(command.menuName())
            .parentId(parentId)
            .menuType(command.menuType())
            .path(command.path())
            .component(resolveComponent(command.menuType(), command.component()))
            .icon(command.icon())
            .sortNo(defaultSortNo(command.sortNo()))
            .status(existing.getStatus())
            .visible(existing.getVisible())
            .minPermissionLevel(command.minPermissionLevel())
            .remark(command.remark())
            .build());
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("MENU_UPDATE")
            .bizType("MENU")
            .bizId(String.valueOf(updated.getId()))
            .afterJson(updated.getMenuCode())
            .result("SUCCESS")
            .build());
        return updated;
    }

    @Transactional
    public void deleteMenu(DeleteMenuCommand command) {
        permissionLevelRuleService.checkCanManageMenu(command.operator());
        Menu menu = menuRepository.findById(command.menuId())
            .orElseThrow(() -> new BizException("MENU_NOT_FOUND", "菜单不存在"));
        if (menuRepository.existsChildren(command.menuId())) {
            throw new BizException("MENU_DELETE_FORBIDDEN", "当前菜单存在子菜单，不能直接删除");
        }
        menuRepository.removeRoleBindings(command.menuId());
        menuRepository.delete(command.menuId());
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("MENU_DELETE")
            .bizType("MENU")
            .bizId(String.valueOf(command.menuId()))
            .afterJson(menu.getMenuCode())
            .result("SUCCESS")
            .build());
    }

    private List<Long> expandMenuIdsWithAncestors(List<Menu> menus) {
        LinkedHashSet<Long> menuIds = new LinkedHashSet<>();
        for (Menu menu : menus) {
            collectMenuWithAncestors(menu, menuIds);
        }
        return new ArrayList<>(menuIds);
    }

    private void collectMenuWithAncestors(Menu menu, LinkedHashSet<Long> menuIds) {
        if (menu.getParentId() != null && menu.getParentId() > 0) {
            Menu parent = menuRepository.findById(menu.getParentId())
                .orElseThrow(() -> new BizException("MENU_PARENT_NOT_FOUND", "菜单父节点不存在"));
            collectMenuWithAncestors(parent, menuIds);
        }
        menuIds.add(menu.getId());
    }

    private void bindMenuToAdmin(Long menuId) {
        Role adminRole = roleRepository.findByCode("ADMIN")
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "管理员角色不存在"));
        menuRepository.bindRole(adminRole.getId(), menuId);
    }

    private void bindMenuToBuiltInManagers(Menu menu) {
        List<Role> builtInRoles = roleRepository.findAll().stream()
            .filter(role -> role.getBuiltIn() != null && role.getBuiltIn() == 1)
            .filter(role -> role.getStatus() != null && role.getStatus() == 1)
            .filter(role -> role.getPermissionLevel() != null && role.getPermissionLevel() <= menu.getMinPermissionLevel())
            .toList();
        if (builtInRoles.isEmpty()) {
            throw new BizException("ROLE_NOT_FOUND", "内置管理角色不存在");
        }
        for (Role role : builtInRoles) {
            menuRepository.bindRole(role.getId(), menu.getId());
        }
    }

    private void validateMenuPayload(
        Long parentId,
        MenuType menuType,
        String path,
        String component,
        Long selfMenuId
    ) {
        if (menuType == null) {
            throw new BizException("MENU_TYPE_REQUIRED", "菜单类型不能为空");
        }
        if (path == null || path.isBlank()) {
            throw new BizException("MENU_PATH_REQUIRED", "菜单路由不能为空");
        }
        if (menuType == MenuType.MENU && (component == null || component.isBlank())) {
            throw new BizException("MENU_COMPONENT_REQUIRED", "菜单组件路径不能为空");
        }
        if (parentId == null || parentId <= 0) {
            return;
        }
        if (selfMenuId != null && selfMenuId.equals(parentId)) {
            throw new BizException("MENU_PARENT_INVALID", "父菜单不能选择自身");
        }
        Menu parent = menuRepository.findById(parentId)
            .orElseThrow(() -> new BizException("MENU_PARENT_NOT_FOUND", "父菜单不存在"));
        if (parent.getMenuType() != MenuType.CATALOG) {
            throw new BizException("MENU_PARENT_INVALID", "仅目录类型菜单允许挂载子菜单");
        }
        validateParentCycle(parent, selfMenuId);
    }

    private void validateParentCycle(Menu parent, Long selfMenuId) {
        if (selfMenuId == null) {
            return;
        }
        Menu current = parent;
        while (current != null && current.getParentId() != null && current.getParentId() > 0) {
            if (selfMenuId.equals(current.getId())) {
                throw new BizException("MENU_PARENT_INVALID", "父菜单不能选择当前菜单的下级节点");
            }
            current = menuRepository.findById(current.getParentId()).orElse(null);
        }
        if (current != null && selfMenuId.equals(current.getId())) {
            throw new BizException("MENU_PARENT_INVALID", "父菜单不能选择当前菜单的下级节点");
        }
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

    private Integer defaultSortNo(Integer sortNo) {
        return sortNo == null ? 0 : sortNo;
    }

    private String resolveComponent(MenuType menuType, String component) {
        if (menuType == MenuType.CATALOG && (component == null || component.isBlank())) {
            return "Layout";
        }
        return component;
    }

    private void ensureRoleExists(Long roleId) {
        roleRepository.findById(roleId)
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
    }

    private void validateRoleDeletion(Role role, String operator) {
        permissionLevelRuleService.checkCanDeleteRole(operator, role);
        if (roleRepository.existsUserBinding(role.getId())) {
            throw new BizException("ROLE_IN_USE", "当前角色已绑定用户，不能直接删除");
        }
    }

    private void deleteRoleInternal(Role role, String operator) {
        roleRepository.delete(role.getId());
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("ROLE_DELETE")
            .bizType("ROLE")
            .bizId(String.valueOf(role.getId()))
            .afterJson(role.getRoleCode())
            .result("SUCCESS")
            .build());
    }

    private void applyDefaultAccessGrants(Role role, boolean createOperation) {
        List<Permission> allPermissions = permissionRepository.findAll();
        if (role.getPermissionLevel() != null && role.getPermissionLevel() <= FULL_ACCESS_PERMISSION_LEVEL) {
            List<Long> permissionIds = allPermissions.stream()
                .map(Permission::getId)
                .toList();
            List<Long> menuIds = menuRepository.findAllEnabled().stream()
                .map(Menu::getId)
                .toList();
            roleRepository.assignPermissions(role.getId(), permissionIds);
            roleRepository.bindMenus(role.getId(), menuIds);
            policyRefreshService.refresh();
            return;
        }

        if (!createOperation) {
            return;
        }

        allPermissions.stream()
            .filter(permission -> AUTH_ME_PERMISSION_CODE.equals(permission.getPermissionCode()))
            .findFirst()
            .ifPresent(permission -> {
                roleRepository.assignPermissions(role.getId(), List.of(permission.getId()));
                policyRefreshService.refresh();
            });
    }
}
