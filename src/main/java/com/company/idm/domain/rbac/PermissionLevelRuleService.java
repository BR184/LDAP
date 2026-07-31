package com.company.idm.domain.rbac;

import com.company.idm.domain.user.User;
import java.util.List;

/**
 * 定义权限等级规则服务，负责谁能操作谁的业务判断。
 */
public interface PermissionLevelRuleService {

    int getEffectivePermissionLevel(String username);

    boolean isAdmin(String username);

    void checkCanModifyBasicUser(String operatorUsername, User targetUser);

    void checkCanModifySensitiveUser(String operatorUsername, User targetUser);

    void checkCanCreateRole(String operatorUsername, Integer newPermissionLevel);

    void checkCanUpdateRole(String operatorUsername, Role targetRole, Integer newPermissionLevel);

    void checkCanDeleteRole(String operatorUsername, Role targetRole);

    void checkCanAssignRoles(String operatorUsername, User targetUser, List<Role> assignedRoles);

    void checkCanManageMenu(String operatorUsername);

    void checkCanManageDepartment(String operatorUsername);
}
