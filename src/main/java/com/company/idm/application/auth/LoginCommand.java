package com.company.idm.application.auth;

/**
 * 封装登录流程的登录标识和密码参数。
 */
public record LoginCommand(String loginId, String password) {
}

