package com.company.idm.domain.rolegroup;

/**
 * 角色成员变更域事件：由仓储写入变更记录后发布，事务提交后触发推送。
 */
public record RoleMembershipChangedEvent(Long changeId) {
}
