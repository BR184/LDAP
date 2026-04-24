package com.company.idm.interfaces.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 封装忘记密码接口的请求参数。
 */
public record ForgotPasswordRequest(
    @NotBlank(message = "用户名不能为空") String username
) {
}
