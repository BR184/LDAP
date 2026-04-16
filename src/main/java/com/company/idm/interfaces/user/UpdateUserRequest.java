package com.company.idm.interfaces.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 封装更新用户资料接口的请求参数。
 */
public record UpdateUserRequest(
    @NotBlank(message = "姓名不能为空") String realName,
    @Email(message = "邮箱格式错误") String email,
    String mobile,
    String employeeNo,
    String deptCode
) {
}

