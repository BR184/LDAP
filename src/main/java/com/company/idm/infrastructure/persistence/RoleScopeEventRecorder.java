package com.company.idm.infrastructure.persistence;

import com.company.idm.domain.rolegroup.RoleMembershipChangedEvent;
import com.company.idm.domain.rolegroup.RoleScopeVersionRepository;
import com.company.idm.domain.rolegroup.RoleSupplyEventDraft;
import com.company.idm.domain.rolegroup.RoleSupplyEventRecorder;
import com.company.idm.domain.rolegroup.RoleSupplyEventType;
import com.company.idm.infrastructure.persistence.dataobject.RoleMembershipChangeDO;
import com.company.idm.infrastructure.persistence.mapper.RoleMembershipChangeMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 角色供给事件记录器：把授权事实变化写入事件表，并在同一事务内分配范围版本。
 *
 * <p>调用方必须处于业务事务中——事件与业务变更同事务成立，回滚时事件一并消失，
 * 提交后即使进程崩溃也能由发布器按未发布标记继续投递（至少一次语义）。
 * 版本分配对所属角色组加行锁，使同组事件严格按提交顺序编号；订阅控制事件
 * 只使用独立控制版本，不参与组范围序列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleScopeEventRecorder implements RoleSupplyEventRecorder {

    private static final int MAX_PAYLOAD_LENGTH = 1024;

    private final RoleMembershipChangeMapper changeMapper;
    private final RoleScopeVersionRepository scopeVersionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 记录一条角色供给事件。
     *
     * @param draft 事件草稿，不可为空
     * @return 事件稳定标识，供消费方幂等
     */
    @Override
    public Long record(RoleSupplyEventDraft draft) {
        if (draft == null || draft.eventType() == null) {
            throw new IllegalArgumentException("角色供给事件缺少类型");
        }
        Long scopeVersion = resolveScopeVersion(draft);
        RoleMembershipChangeDO row = new RoleMembershipChangeDO();
        row.setRoleId(draft.roleId());
        row.setRoleCode(draft.roleCode());
        row.setRoleName(draft.roleName());
        row.setRoleScope(draft.roleScope() == null ? null : draft.roleScope().name());
        row.setRoleGroupId(draft.roleGroupId());
        row.setUserId(draft.userId());
        row.setMemberName(draft.memberName());
        row.setMemberUserId(draft.memberUserId());
        row.setChangeType(draft.changeType());
        row.setEventType(draft.eventType().name());
        row.setScopeVersion(scopeVersion);
        row.setPayload(serializePayload(draft.payload()));
        row.setSubscriptionId(draft.subscriptionId());
        row.setControlVersion(draft.controlVersion());
        row.setOperator(normalizeOperator(draft.operator()));
        row.setGmtCreate(LocalDateTime.now());
        changeMapper.insert(row);
        applicationEventPublisher.publishEvent(new RoleMembershipChangedEvent(row.getId()));
        return row.getId();
    }

    private Long resolveScopeVersion(RoleSupplyEventDraft draft) {
        if (draft.roleGroupId() == null) {
            return null;
        }
        if (draft.eventType() == RoleSupplyEventType.SUBSCRIPTION_CONTROL) {
            return null;
        }
        return scopeVersionRepository.nextVersion(draft.roleGroupId());
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            return json.length() > MAX_PAYLOAD_LENGTH ? json.substring(0, MAX_PAYLOAD_LENGTH) : json;
        } catch (JsonProcessingException exception) {
            log.warn("角色供给事件载荷序列化失败，按空载荷记录", exception);
            return null;
        }
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator.trim();
    }
}
