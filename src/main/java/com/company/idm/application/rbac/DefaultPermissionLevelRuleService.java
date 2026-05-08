package com.company.idm.application.rbac;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Menu;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultPermissionLevelRuleService implements PermissionLevelRuleService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public int getEffectivePermissionLevel(String userId) {
        Set<String> roleCodes = userRepository.findRoleCodesByUserId(userId);
        return roleRepository.findByCodes(roleCodes).stream()
            .map(Role::getPermissionLevel)
            .min(Comparator.naturalOrder())
            .orElse(Integer.MAX_VALUE);
    }

    @Override
    public boolean isAdmin(String userId) {
        Set<String> roleCodes = userRepository.findRoleCodesByUserId(userId);
        return roleCodes.contains(ADMIN_ROLE_CODE) || roleCodes.contains(SUPER_ADMIN_ROLE_CODE);
    }

    @Override
    public void checkCanModifyBasicUser(String operatorUserId, User targetUser) {
        if (operatorUserId.equals(targetUser.getUserId()) || isSuperAdmin(operatorUserId)) {
            return;
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUserId);
        int targetLevel = getEffectivePermissionLevel(targetUser.getUserId());
        if (!(operatorLevel < targetLevel)) {
            throw new BizException("AUTH_FORBIDDEN", "无权修改当前用户的基础信息");
        }
    }

    @Override
    public void checkCanModifySensitiveUser(String operatorUserId, User targetUser) {
        if (isSuperAdmin(operatorUserId)) {
            return;
        }
        if (!isAdmin(operatorUserId)) {
            throw new BizException("AUTH_FORBIDDEN", "仅管理员允许执行敏感操作");
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUserId);
        int targetLevel = getEffectivePermissionLevel(targetUser.getUserId());
        if (!(operatorLevel < targetLevel)) {
            throw new BizException("AUTH_FORBIDDEN", "当前管理员无权操作目标用户");
        }
    }

    @Override
    public void checkCanCreateRole(String operatorUserId, Integer newPermissionLevel) {
        if (isSuperAdmin(operatorUserId)) {
            return;
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUserId);
        if (!(operatorLevel < newPermissionLevel)) {
            throw new BizException("AUTH_FORBIDDEN", "无权创建当前权限等级的角色");
        }
    }

    @Override
    public void checkCanUpdateRole(String operatorUserId, Role targetRole, Integer newPermissionLevel) {
        if (isSuperAdmin(operatorUserId)) {
            return;
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUserId);
        if (!(operatorLevel < targetRole.getPermissionLevel() && operatorLevel < newPermissionLevel)) {
            throw new BizException("AUTH_FORBIDDEN", "无权修改当前角色");
        }
    }

    @Override
    public void checkCanDeleteRole(String operatorUserId, Role targetRole) {
        if (SUPER_ADMIN_ROLE_CODE.equals(targetRole.getRoleCode())) {
            throw new BizException("ROLE_DELETE_FORBIDDEN", "超级管理员角色不允许删除");
        }
        if (isSuperAdmin(operatorUserId)) {
            return;
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUserId);
        if (!(operatorLevel < targetRole.getPermissionLevel())) {
            throw new BizException("AUTH_FORBIDDEN", "无权删除当前角色");
        }
    }

    @Override
    public void checkCanAssignRoles(String operatorUserId, User targetUser, List<Role> assignedRoles) {
        if (isSuperAdmin(operatorUserId)) {
            return;
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUserId);
        int targetLevel = getEffectivePermissionLevel(targetUser.getUserId());
        if (!(operatorLevel < targetLevel)) {
            throw new BizException("AUTH_FORBIDDEN", "无权修改当前用户角色");
        }
        boolean invalidRole = assignedRoles.stream().anyMatch(role -> operatorLevel >= role.getPermissionLevel());
        if (invalidRole) {
            throw new BizException("AUTH_FORBIDDEN", "无权分配当前角色");
        }
    }

    @Override
    public void checkCanBindMenus(String operatorUserId, Role targetRole, List<Menu> menus) {
        if (!isSuperAdmin(operatorUserId)) {
            int operatorLevel = getEffectivePermissionLevel(operatorUserId);
            if (!(operatorLevel < targetRole.getPermissionLevel())) {
                throw new BizException("AUTH_FORBIDDEN", "无权维护当前角色菜单");
            }
        }
        boolean invalidMenu = menus.stream().anyMatch(menu ->
            menu.getMinPermissionLevel() != null && targetRole.getPermissionLevel() > menu.getMinPermissionLevel()
        );
        if (invalidMenu) {
            throw new BizException("ROLE_MENU_BIND_FORBIDDEN", "角色权限等级不足以绑定目标菜单");
        }
    }

    @Override
    public void checkCanManageMenu(String operatorUserId) {
        if (!isAdmin(operatorUserId)) {
            throw new BizException("AUTH_FORBIDDEN", "仅管理员允许维护菜单");
        }
    }

    @Override
    public void checkCanManageDepartment(String operatorUserId) {
        if (!isAdmin(operatorUserId)) {
            throw new BizException("AUTH_FORBIDDEN", "仅管理员允许维护部门");
        }
    }

    private boolean isSuperAdmin(String userId) {
        return userRepository.findRoleCodesByUserId(userId).contains(SUPER_ADMIN_ROLE_CODE);
    }
}
