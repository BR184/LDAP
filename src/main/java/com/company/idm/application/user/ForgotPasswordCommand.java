package com.company.idm.application.user;

/**
 * 封装忘记密码请求的应用层参数。
 */
public record ForgotPasswordCommand(String username, String clientIp) {
}
