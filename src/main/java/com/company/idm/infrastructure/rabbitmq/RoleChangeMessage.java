package com.company.idm.infrastructure.rabbitmq;

import com.company.idm.domain.rolegroup.RoleMembershipChange;
import java.time.LocalDateTime;

/**
 * 推送消息体：字段与 /api/v2/open/role-supply/changes 响应一致，并附加 eventId 供第三方幂等。
 */
public record RoleChangeMessage(
    Long eventId,
    String changeType,
    Long roleId,
    String roleCode,
    String roleName,
    String roleScope,
    Long roleGroupId,
    String memberName,
    String userId,
    LocalDateTime gmtCreate
) {

    public static RoleChangeMessage from(RoleMembershipChange change) {
        return new RoleChangeMessage(
            change.id(),
            change.changeType(),
            change.roleId(),
            change.roleCode(),
            change.roleName(),
            change.roleScope() == null ? null : change.roleScope().name(),
            change.roleGroupId(),
            change.memberName(),
            change.memberUserId(),
            change.gmtCreate()
        );
    }
}
