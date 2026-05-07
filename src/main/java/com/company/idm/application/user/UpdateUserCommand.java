package com.company.idm.application.user;

import java.util.List;

/**
 * 封装更新用户基础资料时的应用层命令参数。
 */
public record UpdateUserCommand(
    Long userId,
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
        String realName,
        String email,
        String intranetEmail,
        String mobile,
        String employeeNo,
        String deptCode,
        String operator
    ) {
        this(userId, realName, email, intranetEmail, mobile, employeeNo, deptCode, List.of(), operator);
    }
}
