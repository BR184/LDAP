package com.company.idm.application.user;

/**
 * 封装用户修改本人密码时的应用层命令参数。
 */
public record ChangePasswordCommand(
    String operator,
    String oldPassword,
    String newPassword,
    String confirmPassword
) {
}

