package com.company.idm.interfaces.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 封装更新用户资料接口的请求参数。
 */
public record UpdateUserRequest(
    @NotBlank(message = "姓名不能为空") String realName,
    @Email(message = "邮箱格式错误") String email,
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式错误") String mobile,
    String employeeNo,
    String deptCode
) {
}
