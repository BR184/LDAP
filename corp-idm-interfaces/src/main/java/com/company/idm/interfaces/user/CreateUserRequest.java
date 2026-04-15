package com.company.idm.interfaces.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateUserRequest(
    @NotBlank(message = "用户名不能为空") String username,
    @NotBlank(message = "姓名不能为空") String realName,
    @Email(message = "邮箱格式错误") String email,
    String mobile,
    String employeeNo,
    String deptCode,
    @NotBlank(message = "初始密码不能为空") String initialPassword,
    @NotEmpty(message = "角色不能为空") List<Long> roleIds
) {
}

