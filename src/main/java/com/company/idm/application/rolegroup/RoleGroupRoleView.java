package com.company.idm.application.rolegroup;

import com.company.idm.domain.rbac.RoleScope;

public record RoleGroupRoleView(
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
