package com.company.idm.application.rolegroup;

import com.company.idm.domain.rbac.RoleScope;
import java.util.List;

public record RoleSupplyRoleSnapshot(
    Long roleId,
    String roleCode,
    String roleName,
    RoleScope roleScope,
    Long roleGroupId,
    List<String> memberNames
) {
}
