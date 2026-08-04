package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.domain.rolegroup.RoleGroup;
import com.company.idm.domain.rolegroup.RoleGroupMember;
import com.company.idm.domain.rolegroup.RoleGroupMemberRole;
import com.company.idm.domain.rolegroup.RoleGroupRepository;
import com.company.idm.infrastructure.persistence.dataobject.RoleGroupDO;
import com.company.idm.infrastructure.persistence.dataobject.RoleGroupMemberDO;
import com.company.idm.infrastructure.persistence.mapper.RoleGroupMapper;
import com.company.idm.infrastructure.persistence.mapper.RoleGroupMemberMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisRoleGroupRepository implements RoleGroupRepository {

    private final RoleGroupMapper roleGroupMapper;
    private final RoleGroupMemberMapper memberMapper;

    @Override
    public RoleGroup save(RoleGroup roleGroup) {
        RoleGroupDO dataObject = toDataObject(roleGroup);
        LocalDateTime now = LocalDateTime.now();
        if (dataObject.getId() == null) {
            dataObject.setGmtCreate(roleGroup.getGmtCreate() == null ? now : roleGroup.getGmtCreate());
            dataObject.setGmtModified(roleGroup.getGmtModified() == null ? now : roleGroup.getGmtModified());
            roleGroupMapper.insert(dataObject);
        } else {
            dataObject.setGmtModified(roleGroup.getGmtModified() == null ? now : roleGroup.getGmtModified());
            roleGroupMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public Optional<RoleGroup> findById(Long groupId) {
        return Optional.ofNullable(roleGroupMapper.selectById(groupId)).map(this::toDomain);
    }

    @Override
    public void lockById(Long groupId) {
        roleGroupMapper.lockById(groupId);
    }

    @Override
    public List<RoleGroup> findAll() {
        return roleGroupMapper.selectList(new LambdaQueryWrapper<RoleGroupDO>()
                .eq(RoleGroupDO::getStatus, 1)
                .orderByAsc(RoleGroupDO::getGroupName)
                .orderByAsc(RoleGroupDO::getId))
            .stream().map(this::toDomain).toList();
    }

    @Override
    public List<RoleGroup> findByMemberUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Long> groupIds = memberMapper.selectList(new LambdaQueryWrapper<RoleGroupMemberDO>()
                .eq(RoleGroupMemberDO::getUserId, userId))
            .stream().map(RoleGroupMemberDO::getGroupId).distinct().toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return roleGroupMapper.selectBatchIds(groupIds).stream()
            .filter(group -> Integer.valueOf(1).equals(group.getStatus()))
            .map(this::toDomain)
            .sorted(java.util.Comparator.comparing(RoleGroup::getGroupName).thenComparing(RoleGroup::getId))
            .toList();
    }

    @Override
    public Optional<RoleGroupMember> findMember(Long groupId, Long userId) {
        RoleGroupMemberDO member = memberMapper.selectOne(new LambdaQueryWrapper<RoleGroupMemberDO>()
            .eq(RoleGroupMemberDO::getGroupId, groupId)
            .eq(RoleGroupMemberDO::getUserId, userId));
        if (member == null) {
            return Optional.empty();
        }
        String realName = memberMapper.selectMembers(groupId).stream()
            .filter(item -> userId.equals(item.userId()))
            .map(item -> item.realName())
            .findFirst().orElse(null);
        return Optional.of(new RoleGroupMember(
            groupId,
            userId,
            realName,
            RoleGroupMemberRole.valueOf(member.getMemberRole())
        ));
    }

    @Override
    public List<RoleGroupMember> findMembers(Long groupId) {
        return memberMapper.selectMembers(groupId).stream()
            .map(item -> new RoleGroupMember(
                item.groupId(),
                item.userId(),
                item.realName(),
                RoleGroupMemberRole.valueOf(item.memberRole())
            ))
            .toList();
    }

    @Override
    public void saveMember(RoleGroupMember member, String operator) {
        RoleGroupMemberDO existing = memberMapper.selectOne(new LambdaQueryWrapper<RoleGroupMemberDO>()
            .eq(RoleGroupMemberDO::getGroupId, member.groupId())
            .eq(RoleGroupMemberDO::getUserId, member.userId()));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            RoleGroupMemberDO relation = new RoleGroupMemberDO();
            relation.setGroupId(member.groupId());
            relation.setUserId(member.userId());
            relation.setMemberRole(member.memberRole().name());
            relation.setCreator(operator);
            relation.setGmtCreate(now);
            relation.setGmtModified(now);
            memberMapper.insert(relation);
            return;
        }
        existing.setMemberRole(member.memberRole().name());
        existing.setGmtModified(now);
        memberMapper.update(existing, new LambdaQueryWrapper<RoleGroupMemberDO>()
            .eq(RoleGroupMemberDO::getGroupId, member.groupId())
            .eq(RoleGroupMemberDO::getUserId, member.userId()));
    }

    @Override
    public void removeMember(Long groupId, Long userId) {
        memberMapper.delete(new LambdaQueryWrapper<RoleGroupMemberDO>()
            .eq(RoleGroupMemberDO::getGroupId, groupId)
            .eq(RoleGroupMemberDO::getUserId, userId));
    }

    @Override
    public long countOwners(Long groupId) {
        return memberMapper.selectCount(new LambdaQueryWrapper<RoleGroupMemberDO>()
            .eq(RoleGroupMemberDO::getGroupId, groupId)
            .eq(RoleGroupMemberDO::getMemberRole, RoleGroupMemberRole.OWNER.name()));
    }

    @Override
    public void delete(Long groupId) {
        memberMapper.delete(new LambdaQueryWrapper<RoleGroupMemberDO>()
            .eq(RoleGroupMemberDO::getGroupId, groupId));
        roleGroupMapper.deleteById(groupId);
    }

    private RoleGroup toDomain(RoleGroupDO dataObject) {
        return RoleGroup.builder()
            .id(dataObject.getId())
            .groupName(dataObject.getGroupName())
            .remark(dataObject.getRemark())
            .status(dataObject.getStatus())
            .creator(dataObject.getCreator())
            .modifier(dataObject.getModifier())
            .gmtCreate(dataObject.getGmtCreate())
            .gmtModified(dataObject.getGmtModified())
            .build();
    }

    private RoleGroupDO toDataObject(RoleGroup roleGroup) {
        RoleGroupDO dataObject = new RoleGroupDO();
        dataObject.setId(roleGroup.getId());
        dataObject.setGroupName(roleGroup.getGroupName());
        dataObject.setRemark(roleGroup.getRemark());
        dataObject.setStatus(roleGroup.getStatus());
        dataObject.setCreator(roleGroup.getCreator());
        dataObject.setModifier(roleGroup.getModifier());
        dataObject.setGmtCreate(roleGroup.getGmtCreate());
        dataObject.setGmtModified(roleGroup.getGmtModified());
        return dataObject;
    }
}
