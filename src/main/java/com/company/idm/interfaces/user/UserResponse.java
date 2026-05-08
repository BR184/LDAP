package com.company.idm.interfaces.user;

import java.util.List;
import java.util.Set;

/**
 * 封装用户接口的响应数据。
 */
public record UserResponse(
    Long id,
    String userId,
    String realName,
    String email,
    String intranetEmail,
    String mobile,
    String employeeNo,
    String deptName,
    String deptCode,
    String jobTitle,
    String directLeaderRaw,
    String leaderRef,
    String accountStatus,
    List<String> partTimeDeptCodes,
    List<String> partTimeDeptNames,
    Integer permissionLevel,
    Integer status,
    String employmentStatus,
    String ldapDn,
    Set<String> roleCodes
) {
}

