package com.company.idm.interfaces.rolegroup;

import com.company.idm.domain.rolegroup.RoleGroupMemberRole;

public record RoleGroupMemberResponse(Long userId, String realName, RoleGroupMemberRole memberRole) {
}
