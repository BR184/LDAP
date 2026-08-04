package com.company.idm.infrastructure.persistence.record;

public record RoleGroupMemberRecord(
    Long groupId,
    Long userId,
    String realName,
    String memberRole
) {
}
