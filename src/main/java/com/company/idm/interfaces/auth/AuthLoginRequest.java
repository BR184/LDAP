package com.company.idm.interfaces.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 封装登录接口的请求参数。
 */
public record AuthLoginRequest(
    @NotBlank(message = "工号不能为空") String username,
    @NotBlank(message = "密码不能为空") String password
) {
}

