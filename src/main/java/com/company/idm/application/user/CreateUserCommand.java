package com.company.idm.application.user;

import java.util.List;

/**
 * 封装创建用户时的应用层命令参数。
 */
public record CreateUserCommand(
    String username,
    String realName,
    String email,
    String mobile,
    String employeeNo,
    String deptCode,
    String initialPassword,
    List<Long> roleIds,
    String operator
) {
}

