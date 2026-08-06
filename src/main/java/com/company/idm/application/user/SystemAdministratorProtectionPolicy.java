package com.company.idm.application.user;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.user.User;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Defines the non-bypassable safety rules for the built-in administrator account.
 */
@Component
public class SystemAdministratorProtectionPolicy {

    private static final String SYSTEM_ADMIN_USER_ID = "admin";
    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    public boolean isSystemAdministrator(String userId) {
        return userId != null && SYSTEM_ADMIN_USER_ID.equalsIgnoreCase(userId.trim());
    }

    public void checkUserIdAvailableForCreation(String userId) {
        if (isSystemAdministrator(userId)) {
            throw new BizException("SYSTEM_ADMIN_USER_ID_RESERVED", "admin 是系统保留账号，不允许创建同名用户");
        }
    }

    public void checkProfileMutable(User user) {
        if (isSystemAdministrator(user)) {
            throw new BizException("SYSTEM_ADMIN_PROFILE_LOCKED", "内置 admin 账号的身份资料不允许修改");
        }
    }

    public void checkAccessChangeAllowed(User user, boolean accessAllowed) {
        if (isSystemAdministrator(user) && !accessAllowed) {
            throw new BizException("SYSTEM_ADMIN_ACCESS_REQUIRED", "内置 admin 账号必须保持允许使用");
        }
    }

    public void checkDeletable(User user) {
        if (isSystemAdministrator(user)) {
            throw new BizException("USER_DELETE_SYSTEM_ADMIN_FORBIDDEN", "不允许删除内置 admin 账号");
        }
    }

    public void checkImportTargetAllowed(String userId) {
        if (isSystemAdministrator(userId)) {
            throw new BizException("SYSTEM_ADMIN_IMPORT_FORBIDDEN", "文件导入不允许创建、更新、离职或撤回内置 admin 账号");
        }
    }

    public void checkRetainsSuperAdministratorRole(User user, List<Role> requestedRoles) {
        if (!isSystemAdministrator(user)) {
            return;
        }
        boolean retainsSuperAdmin = requestedRoles != null && requestedRoles.stream()
            .anyMatch(role -> SUPER_ADMIN_ROLE_CODE.equals(role.getRoleCode()));
        if (!retainsSuperAdmin) {
            throw new BizException(
                "SYSTEM_ADMIN_SUPER_ADMIN_REQUIRED",
                "内置 admin 账号必须保留超级管理员角色"
            );
        }
    }

    private boolean isSystemAdministrator(User user) {
        return user != null && isSystemAdministrator(user.getUserId());
    }
}
