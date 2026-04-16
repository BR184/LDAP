package com.company.idm.application.rbac;

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
        roleRepository.bindMenus(command.roleId(), command.menuIds());
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("ROLE_MENU_BIND")
            .bizType("ROLE")
            .bizId(String.valueOf(command.roleId()))
            .afterJson(String.valueOf(command.menuIds()))
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
}
