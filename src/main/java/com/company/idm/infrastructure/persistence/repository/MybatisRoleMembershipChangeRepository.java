package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.persistence.dataobject.RoleMembershipChangeDO;
import com.company.idm.infrastructure.persistence.mapper.RoleMembershipChangeMapper;
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

    private RoleMembershipChange toDomain(RoleMembershipChangeDO item) {
        return new RoleMembershipChange(
            item.getId(),
            item.getRoleId(),
            item.getRoleCode(),
            item.getRoleName(),
            RoleScope.valueOf(item.getRoleScope()),
            item.getRoleGroupId(),
            item.getMemberName(),
            item.getChangeType(),
            item.getGmtCreate()
        );
    }
}
