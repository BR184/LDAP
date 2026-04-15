package com.company.idm.interfaces.user;

import java.util.Set;

/**
 * 封装用户接口的响应数据。
 */
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

