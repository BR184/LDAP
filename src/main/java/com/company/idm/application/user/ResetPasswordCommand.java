package com.company.idm.application.user;

/**
 * 封装管理员重置用户密码时的应用层命令参数。
 */
public record ResetPasswordCommand(Long userId, String operator) {
}

