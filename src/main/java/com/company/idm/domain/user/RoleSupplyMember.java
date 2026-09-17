package com.company.idm.domain.user;

/**
 * 角色供应成员视图：平台用户ID（飞书ID）与公开姓名。
 */
public record RoleSupplyMember(String platformUserId, String realName) {
}
