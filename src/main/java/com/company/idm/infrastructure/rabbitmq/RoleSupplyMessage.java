package com.company.idm.infrastructure.rabbitmq;

import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 角色供给推送消息体（协议版本 2）。
 *
 * <p>在既有字段（eventId/changeType/roleCode/memberName/userId 等）基础上加法扩展：
 * 消息类型与协议版本用于区分业务事件与状态心跳；范围版本是消费方判断新旧与补偿
 * 完整性的依据；角色以稳定标识识别，同编码删除再建视为新角色。旧消费方忽略新增
 * 字段即可继续工作，因此本结构不构成破坏性变更。
 */
public record RoleSupplyMessage(
    int protocolVersion,
    String messageType,
    String sourceId,
    Long eventId,
    String eventType,
    String changeType,
    Long roleId,
    String roleCode,
    String roleName,
    String roleScope,
    Long roleGroupId,
    Long scopeVersion,
    String memberName,
    String userId,
    LocalDateTime gmtCreate,
    Map<String, Object> payload,
    Long subscriptionId,
    Long controlVersion
) {

    /** 业务事件消息类型。 */
    public static final String TYPE_ROLE_CHANGE = "ROLE_CHANGE";

    /**
     * 由持久化事件构造推送消息。
     *
     * @param change 事件事实
     * @param protocolVersion 当前协议版本
     * @param sourceId 稳定来源标识
     * @param objectMapper 用于把结构化载荷还原为对象，解析失败时按原始文本传递
     * @return 推送消息
     */
    public static RoleSupplyMessage from(
        RoleMembershipChange change,
        int protocolVersion,
        String sourceId,
        ObjectMapper objectMapper
    ) {
        return new RoleSupplyMessage(
            protocolVersion,
            TYPE_ROLE_CHANGE,
            sourceId,
            change.id(),
            change.eventType() == null ? null : change.eventType().name(),
            change.changeType(),
            change.roleId(),
            change.roleCode(),
            change.roleName(),
            change.roleScope() == null ? null : change.roleScope().name(),
            change.roleGroupId(),
            change.scopeVersion(),
            change.memberName(),
            change.memberUserId(),
            change.gmtCreate(),
            parsePayload(change.payload(), objectMapper),
            change.subscriptionId(),
            change.controlVersion()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parsePayload(String payload, ObjectMapper objectMapper) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception exception) {
            return Map.of("raw", payload);
        }
    }
}
