package com.company.idm.application.user;

/**
 * 封装变更用户启停状态的应用层命令参数。
 */
public record UpdateUserStatusCommand(Long userId, Integer statusCode, String operator) {
}

