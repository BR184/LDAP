package com.company.idm.interfaces.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 封装创建用户接口的请求参数。
 */
public record CreateUserRequest(
    @NotBlank(message = "用户ID不能为空") String userId,
    @NotBlank(message = "姓名不能为空") String realName,
    @Email(message = "工作邮箱格式错误") String email,
    @NotBlank(message = "内网邮箱不能为空") @Email(message = "内网邮箱格式错误") String intranetEmail,
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式错误") String mobile,
    @NotBlank(message = "工号不能为空") String employeeNo,
    String deptCode,
    List<String> partTimeDeptCodes,
    @NotNull(message = "允许使用不能为空") Boolean accessAllowed,
    @NotEmpty(message = "角色不能为空") List<Long> roleIds
) {
}
