package com.company.idm.interfaces.user;

import jakarta.validation.constraints.NotBlank;

/**
 * 校验当前用户旧密码的请求参数。
 */
public record VerifyPasswordRequest(
    @NotBlank(message = "旧密码不能为空") String oldPassword
) {
}
