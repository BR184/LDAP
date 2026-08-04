package com.company.idm.interfaces.rolegroup;

import com.company.idm.domain.rolegroup.RoleGroupMemberRole;
import java.time.LocalDateTime;
import java.util.List;

public record RoleGroupResponse(
    Long id,
    String groupName,
    String remark,
    Integer status,
    RoleGroupMemberRole currentMemberRole,
    String creatorName,
    List<String> ownerNames,
    long memberCount,
    long roleCount,
    LocalDateTime gmtCreate,
    LocalDateTime gmtModified
) {
}
