package com.company.idm.application.user;

import java.util.List;

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

