package com.company.idm.interfaces.user;

import java.util.List;
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
    String deptName,
    String deptCode,
    List<String> partTimeDeptCodes,
    List<String> partTimeDeptNames,
    Integer permissionLevel,
    Integer status,
    String ldapDn,
    Set<String> roleCodes
) {
}

