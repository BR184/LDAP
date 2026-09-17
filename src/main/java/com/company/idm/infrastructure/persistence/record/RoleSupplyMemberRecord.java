package com.company.idm.infrastructure.persistence.record;

/**
 * 角色供应成员投影：平台用户ID（飞书ID）与公开姓名，按角色成员稳定排序返回。
 */
public record RoleSupplyMemberRecord(String platformUserId, String realName) {
}
