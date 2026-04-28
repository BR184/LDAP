package com.company.idm.interfaces.role;

/**
 * 封装批量删除角色接口响应结果。
 */
public record BatchDeleteRolesResponse(int totalCount, int deletedCount) {
}
