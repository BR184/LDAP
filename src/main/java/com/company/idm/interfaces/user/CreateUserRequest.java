package com.company.idm.interfaces.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 封装创建用户接口的请求参数。
 */
public record CreateUserRequest(
    @NotBlank(message = "用户名不能为空") String username,
    @NotBlank(message = "姓名不能为空") String realName,
    @Email(message = "邮箱格式错误") String email,
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式错误") String mobile,
    String employeeNo,
    String deptCode,
    @NotBlank(message = "初始密码不能为空") String initialPassword,
    @NotEmpty(message = "角色不能为空") List<Long> roleIds
) {
}

