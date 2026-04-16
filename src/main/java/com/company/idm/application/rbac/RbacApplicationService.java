package com.company.idm.application.rbac;

import com.company.idm.common.exception.BizException;
import com.company.idm.common.enums.MenuType;
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

/**
 * 角色权限应用服务，负责角色、菜单、授权关系与权限规则编排。
 */
@Service
@RequiredArgsConstructor
public class RbacApplicationService {

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PolicyRefreshService policyRefreshService;
    private final PermissionLevelRuleService permissionLevelRuleService;

    /**
     * 查询全部角色。
     */
    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    /**
     * 查询角色详情。
     */
    public Role getRole(Long roleId) {
        return roleRepository.findById(roleId)
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
    }

    /**
     * 查询接口权限点列表。
     */
    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * 查询全部启用菜单，用于后台配置菜单树。
     */
    public List<Menu> listAllMenus() {
        return menuRepository.findAllEnabled();
    }

    /**
     * 查询菜单详情。
     */
    public Menu getMenu(Long menuId) {
        return menuRepository.findById(menuId)
            .orElseThrow(() -> new BizException("MENU_NOT_FOUND", "菜单不存在"));
    }

    /**
     * 查询当前用户可见菜单树数据。
     */
    public List<Menu> listCurrentUserMenus(String username) {
        Set<String> roleCodes = userRepository.findRoleCodesByUsername(username);
        return menuRepository.findByRoleCodes(roleCodes);
    }

    /**
     * 创建角色并设置权限等级。
     */
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

    /**
     * 更新角色基础信息和权限等级。
     */
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

    /**
     * 更新角色启停状态。
     */
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

    /**
     * 删除角色。
     */
    @Transactional
    public void deleteRole(DeleteRoleCommand command) {
        Role role = roleRepository.findById(command.roleId())
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
        permissionLevelRuleService.checkCanDeleteRole(command.operator(), role);
        if (roleRepository.existsUserBinding(command.roleId())) {
            throw new BizException("ROLE_IN_USE", "当前角色已绑定用户，不能直接删除");
        }
        roleRepository.delete(command.roleId());
        policyRefreshService.refresh();
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("ROLE_DELETE")
            .bizType("ROLE")
            .bizId(String.valueOf(command.roleId()))
            .afterJson(role.getRoleCode())
            .result("SUCCESS")
            .build());
    }

    /**
     * 保留当前原型的接口权限点授权能力。
     */
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

    /**
     * 绑定角色与菜单关系，供前端菜单树渲染使用。
     */
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

    /**
     * 给用户分配角色。
     */
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

    /**
     * 创建菜单并自动绑定到管理员角色。
     */
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
        bindMenuToAdmin(menu.getId());
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

    /**
     * 更新菜单基础信息并保持现有绑定约束合法。
     */
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

    /**
     * 删除菜单，同时清理角色与菜单关系。
     */
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

    /**
     * 自动补齐祖先菜单，避免前端菜单树渲染时出现孤立叶子节点。
     */
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
}
