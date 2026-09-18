package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.domain.rolegroup.RoleSupplyEventType;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.persistence.dataobject.RoleMembershipChangeDO;
import com.company.idm.infrastructure.persistence.mapper.RoleMembershipChangeMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisRoleMembershipChangeRepository implements RoleMembershipChangeRepository {

    private final RoleMembershipChangeMapper mapper;

    @Override
    public long currentCursor() {
        RoleMembershipChangeDO latest = mapper.selectOne(new LambdaQueryWrapper<RoleMembershipChangeDO>()
            .select(RoleMembershipChangeDO::getId)
            .orderByDesc(RoleMembershipChangeDO::getId)
            .last("LIMIT 1"));
        return latest == null || latest.getId() == null ? 0L : latest.getId();
    }

    @Override
    public List<RoleMembershipChange> findAfter(
        long cursor,
        int limit,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId
    ) {
        int boundedLimit = Math.max(1, Math.min(limit, 501));
        LambdaQueryWrapper<RoleMembershipChangeDO> query = new LambdaQueryWrapper<RoleMembershipChangeDO>()
            .gt(RoleMembershipChangeDO::getId, Math.max(0, cursor))
            .orderByAsc(RoleMembershipChangeDO::getId)
            .last("LIMIT " + boundedLimit);
        if (subjectType == PersonalAccessTokenSubjectType.ROLE_GROUP && subjectId != null) {
            query.and(scope -> scope
                .eq(RoleMembershipChangeDO::getRoleScope, RoleScope.GLOBAL.name())
                .or(group -> group
                    .eq(RoleMembershipChangeDO::getRoleScope, RoleScope.GROUP.name())
                    .eq(RoleMembershipChangeDO::getRoleGroupId, subjectId)));
        } else if (subjectType != PersonalAccessTokenSubjectType.GLOBAL) {
            return List.of();
        }
        return mapper.selectList(query).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RoleMembershipChange> findAfterScopeVersion(Long groupId, long sinceVersion, int limit) {
        if (groupId == null) {
            return List.of();
        }
        int boundedLimit = Math.max(1, Math.min(limit, 501));
        return mapper.selectList(new LambdaQueryWrapper<RoleMembershipChangeDO>()
                .eq(RoleMembershipChangeDO::getRoleGroupId, groupId)
                .isNotNull(RoleMembershipChangeDO::getScopeVersion)
                .gt(RoleMembershipChangeDO::getScopeVersion, Math.max(0, sinceVersion))
                .orderByAsc(RoleMembershipChangeDO::getScopeVersion)
                .orderByAsc(RoleMembershipChangeDO::getId)
                .last("LIMIT " + boundedLimit))
            .stream().map(this::toDomain).toList();
    }

    @Override
    public long earliestScopeVersion(Long groupId) {
        if (groupId == null) {
            return 0L;
        }
        RoleMembershipChangeDO earliest = mapper.selectOne(new LambdaQueryWrapper<RoleMembershipChangeDO>()
            .select(RoleMembershipChangeDO::getScopeVersion)
            .eq(RoleMembershipChangeDO::getRoleGroupId, groupId)
            .isNotNull(RoleMembershipChangeDO::getScopeVersion)
            .orderByAsc(RoleMembershipChangeDO::getScopeVersion)
            .last("LIMIT 1"));
        return earliest == null || earliest.getScopeVersion() == null ? 0L : earliest.getScopeVersion();
    }

    @Override
    public List<RoleMembershipChange> findSubscriptionControlEvents(Long subscriptionId, int limit) {
        if (subscriptionId == null) {
            return List.of();
        }
        int boundedLimit = Math.max(1, Math.min(limit, 501));
        return mapper.selectList(new LambdaQueryWrapper<RoleMembershipChangeDO>()
                .eq(RoleMembershipChangeDO::getSubscriptionId, subscriptionId)
                .eq(RoleMembershipChangeDO::getEventType, RoleSupplyEventType.SUBSCRIPTION_CONTROL.name())
                .orderByAsc(RoleMembershipChangeDO::getControlVersion)
                .orderByAsc(RoleMembershipChangeDO::getId)
                .last("LIMIT " + boundedLimit))
            .stream().map(this::toDomain).toList();
    }

    @Override
    public List<RoleMembershipChange> findUnpublished(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        return mapper.selectList(new LambdaQueryWrapper<RoleMembershipChangeDO>()
                .isNull(RoleMembershipChangeDO::getPublishedAt)
                .orderByAsc(RoleMembershipChangeDO::getId)
                .last("LIMIT " + boundedLimit))
            .stream().map(this::toDomain).toList();
    }

    @Override
    public void markPublished(List<Long> changeIds, LocalDateTime publishedAt) {
        if (changeIds == null || changeIds.isEmpty()) {
            return;
        }
        mapper.update(null, new LambdaUpdateWrapper<RoleMembershipChangeDO>()
            .set(RoleMembershipChangeDO::getPublishedAt, publishedAt)
            .in(RoleMembershipChangeDO::getId, changeIds)
            .isNull(RoleMembershipChangeDO::getPublishedAt));
    }

    @Override
    public int deleteOlderThan(LocalDateTime threshold, int limit) {
        if (threshold == null) {
            return 0;
        }
        int boundedLimit = Math.max(1, Math.min(limit, 5000));
        List<RoleMembershipChangeDO> expired = mapper.selectList(new LambdaQueryWrapper<RoleMembershipChangeDO>()
            .select(RoleMembershipChangeDO::getId)
            .lt(RoleMembershipChangeDO::getGmtCreate, threshold)
            .isNotNull(RoleMembershipChangeDO::getPublishedAt)
            .orderByAsc(RoleMembershipChangeDO::getId)
            .last("LIMIT " + boundedLimit));
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = expired.stream().map(RoleMembershipChangeDO::getId).toList();
        return mapper.delete(new LambdaQueryWrapper<RoleMembershipChangeDO>()
            .in(RoleMembershipChangeDO::getId, ids));
    }

    private RoleMembershipChange toDomain(RoleMembershipChangeDO item) {
        return new RoleMembershipChange(
            item.getId(),
            parseEventType(item.getEventType(), item.getChangeType()),
            item.getRoleId(),
            item.getRoleCode(),
            item.getRoleName(),
            item.getRoleScope() == null ? null : RoleScope.valueOf(item.getRoleScope()),
            item.getRoleGroupId(),
            item.getScopeVersion(),
            item.getUserId(),
            item.getMemberName(),
            item.getMemberUserId(),
            item.getChangeType(),
            item.getPayload(),
            item.getSubscriptionId(),
            item.getControlVersion(),
            item.getGmtCreate()
        );
    }

    private RoleSupplyEventType parseEventType(String eventType, String changeType) {
        if (eventType != null && !eventType.isBlank()) {
            try {
                return RoleSupplyEventType.valueOf(eventType);
            } catch (IllegalArgumentException ignored) {
                // 未知类型按成员事件回退，保证历史数据可读。
            }
        }
        return "REMOVED".equals(changeType) ? RoleSupplyEventType.MEMBER_REMOVED : RoleSupplyEventType.MEMBER_ADDED;
    }
}
