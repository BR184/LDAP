package com.company.idm.application.user;

/**
 * 封装更新用户基础资料时的应用层命令参数。
 */
public record UpdateUserCommand(
    Long userId,
    String realName,
    String email,
    String mobile,
    String employeeNo,
    String deptCode,
    String operator
) {
}

