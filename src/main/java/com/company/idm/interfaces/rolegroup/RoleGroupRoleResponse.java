package com.company.idm.interfaces.rolegroup;

import com.company.idm.domain.rbac.RoleScope;

public record RoleGroupRoleResponse(
    Long id,
    String roleCode,
    String roleName,
    Integer permissionLevel,
    Integer status,
    String remark,
    RoleScope roleScope,
    Long roleGroupId,
    boolean editable
) {
}
