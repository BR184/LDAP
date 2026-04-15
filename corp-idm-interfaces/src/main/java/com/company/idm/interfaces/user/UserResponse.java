package com.company.idm.interfaces.user;

import java.util.Set;

public record UserResponse(
    Long id,
    String username,
    String realName,
    String email,
    String mobile,
    String employeeNo,
    String deptCode,
    Integer status,
    String ldapDn,
    Set<String> roleCodes
) {
}

