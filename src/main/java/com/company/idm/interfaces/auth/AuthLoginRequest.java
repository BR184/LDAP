package com.company.idm.interfaces.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 封装登录接口的请求参数。
 */
public record AuthLoginRequest(
    @NotBlank(message = "登录标识不能为空") String loginId,
    @NotBlank(message = "密码不能为空") String password
) {
}

