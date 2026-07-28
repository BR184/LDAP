package com.company.idm.application.user;

import java.util.List;

/**
 * 封装创建用户时的应用层命令参数。
 */
public record CreateUserCommand(
    String userId,
    String realName,
    String email,
    String intranetEmail,
    String mobile,
    String employeeNo,
    String deptCode,
    List<String> partTimeDeptCodes,
    List<Long> roleIds,
    String operator
) {
}

