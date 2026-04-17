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

/**
 * 提供基于 permission_level 的对象操作权限判断实现。
 */
@Service
@RequiredArgsConstructor
public class DefaultPermissionLevelRuleService implements PermissionLevelRuleService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public int getEffectivePermissionLevel(String username) {
        Set<String> roleCodes = userRepository.findRoleCodesByUsername(username);
        return roleRepository.findByCodes(roleCodes).stream()
            .map(Role::getPermissionLevel)
            .min(Comparator.naturalOrder())
            .orElse(Integer.MAX_VALUE);
    }

    @Override
    public boolean isAdmin(String username) {
        return userRepository.findRoleCodesByUsername(username).contains(ADMIN_ROLE_CODE);
    }

    @Override
    public void checkCanModifyBasicUser(String operatorUsername, User targetUser) {
        if (operatorUsername.equals(targetUser.getUsername()) || isAdmin(operatorUsername)) {
            return;
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUsername);
        int targetLevel = getEffectivePermissionLevel(targetUser.getUsername());
        if (!(operatorLevel < targetLevel)) {
            throw new BizException("AUTH_FORBIDDEN", "无权修改当前用户的基础信息");
        }
    }

    @Override
    public void checkCanModifySensitiveUser(String operatorUsername, User targetUser) {
        if (!isAdmin(operatorUsername)) {
            throw new BizException("AUTH_FORBIDDEN", "仅管理员允许执行敏感操作");
        }
    }

    @Override
    public void checkCanCreateRole(String operatorUsername, Integer newPermissionLevel) {
        if (isAdmin(operatorUsername)) {
            return;
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUsername);
        if (!(operatorLevel < newPermissionLevel)) {
            throw new BizException("AUTH_FORBIDDEN", "无权创建当前权限等级的角色");
        }
    }

    @Override
    public void checkCanUpdateRole(String operatorUsername, Role targetRole, Integer newPermissionLevel) {
        if (isAdmin(operatorUsername)) {
            return;
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUsername);
        if (!(operatorLevel < targetRole.getPermissionLevel() && operatorLevel < newPermissionLevel)) {
            throw new BizException("AUTH_FORBIDDEN", "无权修改当前角色");
        }
    }

    @Override
    public void checkCanDeleteRole(String operatorUsername, Role targetRole) {
        if (targetRole.getBuiltIn() != null && targetRole.getBuiltIn() == 1) {
            throw new BizException("ROLE_DELETE_FORBIDDEN", "内置角色不允许删除");
        }
        if (isAdmin(operatorUsername)) {
            return;
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUsername);
        if (!(operatorLevel < targetRole.getPermissionLevel())) {
            throw new BizException("AUTH_FORBIDDEN", "无权删除当前角色");
        }
    }

    @Override
    public void checkCanAssignRoles(String operatorUsername, User targetUser, List<Role> assignedRoles) {
        if (isAdmin(operatorUsername)) {
            return;
        }
        int operatorLevel = getEffectivePermissionLevel(operatorUsername);
        int targetLevel = getEffectivePermissionLevel(targetUser.getUsername());
        if (!(operatorLevel < targetLevel)) {
            throw new BizException("AUTH_FORBIDDEN", "无权修改当前用户角色");
        }
        boolean invalidRole = assignedRoles.stream().anyMatch(role -> operatorLevel >= role.getPermissionLevel());
        if (invalidRole) {
            throw new BizException("AUTH_FORBIDDEN", "无权分配当前角色");
        }
    }

    @Override
    public void checkCanBindMenus(String operatorUsername, Role targetRole, List<Menu> menus) {
        if (!isAdmin(operatorUsername)) {
            int operatorLevel = getEffectivePermissionLevel(operatorUsername);
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
    public void checkCanManageMenu(String operatorUsername) {
        if (!isAdmin(operatorUsername)) {
            throw new BizException("AUTH_FORBIDDEN", "仅管理员允许维护菜单");
        }
    }

    @Override
    public void checkCanManageDepartment(String operatorUsername) {
        if (!isAdmin(operatorUsername)) {
            throw new BizException("AUTH_FORBIDDEN", "仅管理员允许维护部门");
        }
    }
}
