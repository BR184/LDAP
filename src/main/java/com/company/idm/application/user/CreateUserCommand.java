package com.company.idm.application.user;

import java.util.List;

/**
 * 封装创建用户时的应用层命令参数。
 */
public record CreateUserCommand(
    String realName,
    String email,
    String intranetEmail,
    String mobile,
    String employeeNo,
    String deptCode,
    List<String> partTimeDeptCodes,
    String initialPassword,
    List<Long> roleIds,
    String operator
) {
    public CreateUserCommand(
        String realName,
        String email,
        String intranetEmail,
        String mobile,
        String employeeNo,
        String deptCode,
        String initialPassword,
        List<Long> roleIds,
        String operator
    ) {
        this(realName, email, intranetEmail, mobile, employeeNo, deptCode, List.of(), initialPassword, roleIds, operator);
    }
}

