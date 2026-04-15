package com.company.idm.infrastructure.persistence.record;

/**
 * 承载用户角色绑定查询结果的数据库记录对象。
 */
public record UserRoleBindingRecord(String username, String roleCode) {
}
