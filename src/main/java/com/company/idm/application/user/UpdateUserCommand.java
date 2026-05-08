package com.company.idm.application.user;

import java.util.List;

/**
 * 封装更新用户基础资料时的应用层命令参数。
 */
public record UpdateUserCommand(
    Long userId,
    String userIdentifier,
    String realName,
    String email,
    String intranetEmail,
    String mobile,
    String employeeNo,
    String deptCode,
    List<String> partTimeDeptCodes,
    String operator
) {
    public UpdateUserCommand(
        Long userId,
        String userIdentifier,
        String realName,
        String email,
        String intranetEmail,
        String mobile,
        String employeeNo,
        String deptCode,
        String operator
    ) {
        this(userId, userIdentifier, realName, email, intranetEmail, mobile, employeeNo, deptCode, List.of(), operator);
    }

    public UpdateUserCommand(
        Long userId,
        String userIdentifier,
        String realName,
        String email,
        String intranetEmail,
        String mobile,
        String employeeNo,
        List<String> partTimeDeptCodes,
        String deptCode,
        String operator
    ) {
        this(userId, userIdentifier, realName, email, intranetEmail, mobile, employeeNo, deptCode, partTimeDeptCodes, operator);
    }
}
