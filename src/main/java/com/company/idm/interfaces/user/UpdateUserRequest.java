package com.company.idm.interfaces.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 封装更新用户资料接口的请求参数。
 */
public record UpdateUserRequest(
    @NotBlank(message = "姓名不能为空") String realName,
    @NotBlank(message = "工作邮箱不能为空")
    @Email(message = "工作邮箱格式错误") String email,
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式错误") String mobile,
    @NotBlank(message = "工号不能为空") String employeeNo,
    String deptCode
    ,
    List<String> partTimeDeptCodes
) {
}
