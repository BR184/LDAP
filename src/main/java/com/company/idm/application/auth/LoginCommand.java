package com.company.idm.application.auth;

/**
 * 封装登录流程的用户名和密码参数。
 */
public record LoginCommand(String username, String password) {
}

