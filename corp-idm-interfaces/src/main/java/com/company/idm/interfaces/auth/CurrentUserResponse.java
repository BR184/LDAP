package com.company.idm.interfaces.auth;

import java.util.Set;

public record CurrentUserResponse(
    Long userId,
    String username,
    String realName,
    String email,
    String mobile,
    String deptCode,
    Set<String> roleCodes
) {
}

