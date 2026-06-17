package com.company.idm.application.user;

/**
 * 封装管理员重置用户密码的请求参数。
 */
public record AdminResetPasswordCommand(Long userId, String operator, String operatorIp) {
}
