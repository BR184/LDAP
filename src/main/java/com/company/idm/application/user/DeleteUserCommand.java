package com.company.idm.application.user;

/**
 * 封装删除用户时的应用层命令参数。
 */
public record DeleteUserCommand(Long userId, String operator) {
}

