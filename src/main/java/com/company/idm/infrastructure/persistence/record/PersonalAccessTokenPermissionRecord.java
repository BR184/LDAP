package com.company.idm.infrastructure.persistence.record;

public record PersonalAccessTokenPermissionRecord(
    Long tokenId,
    Long permissionId,
    String permissionCode,
    String permissionName,
    String resourcePath,
    String action
) {
}
