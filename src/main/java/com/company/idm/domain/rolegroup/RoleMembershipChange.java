package com.company.idm.domain.rolegroup;

import com.company.idm.domain.rbac.RoleScope;
import java.time.LocalDateTime;

public record RoleMembershipChange(
    Long id,
    Long roleId,
    String roleCode,
    String roleName,
    RoleScope roleScope,
    Long roleGroupId,
    String memberName,
    String changeType,
    LocalDateTime gmtCreate
) {
}
