package com.company.idm.application.rbac;

/**
 * 封装批量删除角色执行结果。
 */
public record BatchDeleteRolesResult(int totalCount, int deletedCount) {
}
