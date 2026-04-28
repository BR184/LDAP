package com.company.idm.interfaces.user;

/**
 * 封装批量删除用户接口响应结果。
 */
public record BatchDeleteUsersResponse(int totalCount, int deletedCount) {
}
