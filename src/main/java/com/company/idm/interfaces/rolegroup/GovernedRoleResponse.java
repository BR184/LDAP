package com.company.idm.interfaces.rolegroup;

import com.company.idm.domain.rbac.RoleScope;

public record GovernedRoleResponse(
    Long id,
    String roleCode,
    String roleName,
    Integer permissionLevel,
    Integer builtIn,
    Integer status,
    String remark,
    RoleScope roleScope,
    Long roleGroupId
) {
}
