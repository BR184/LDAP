package com.company.idm.interfaces.user;

import jakarta.validation.constraints.NotBlank;

/**
 * 封装用户本人修改密码接口的请求参数。
 */
public record ChangePasswordRequest(
    @NotBlank(message = "密码验证凭证不能为空") String verificationToken,
    @NotBlank(message = "新密码不能为空") String newPassword,
    @NotBlank(message = "确认密码不能为空") String confirmPassword
) {
}
