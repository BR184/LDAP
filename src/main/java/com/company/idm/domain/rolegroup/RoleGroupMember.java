package com.company.idm.domain.rolegroup;

public record RoleGroupMember(
    Long groupId,
    Long userId,
    String realName,
    RoleGroupMemberRole memberRole
) {
}
