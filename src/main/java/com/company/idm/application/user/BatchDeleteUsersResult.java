package com.company.idm.application.user;

/**
 * 封装批量删除用户执行结果。
 */
public record BatchDeleteUsersResult(int totalCount, int deletedCount) {
}
