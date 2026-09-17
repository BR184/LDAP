package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.idm.domain.rolegroup.PushSubscription;
import com.company.idm.domain.rolegroup.PushSubscriptionRepository;
import com.company.idm.domain.rolegroup.PushSubscriptionStatus;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.persistence.dataobject.PushSubscriptionDO;
import com.company.idm.infrastructure.persistence.dataobject.PushSubscriptionRoleDO;
import com.company.idm.infrastructure.persistence.mapper.PushSubscriptionMapper;
import com.company.idm.infrastructure.persistence.mapper.PushSubscriptionRoleMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisPushSubscriptionRepository implements PushSubscriptionRepository {

    private final PushSubscriptionMapper subscriptionMapper;
    private final PushSubscriptionRoleMapper subscriptionRoleMapper;

    @Override
    public PushSubscription save(PushSubscription subscription) {
        PushSubscriptionDO dataObject = toDataObject(subscription);
        LocalDateTime now = LocalDateTime.now();
        if (dataObject.getId() == null) {
            dataObject.setGmtCreate(subscription.getGmtCreate() == null ? now : subscription.getGmtCreate());
            dataObject.setGmtModified(subscription.getGmtModified() == null ? now : subscription.getGmtModified());
            subscriptionMapper.insert(dataObject);
        } else {
            dataObject.setGmtModified(subscription.getGmtModified() == null ? now : subscription.getGmtModified());
            subscriptionMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public Optional<PushSubscription> findById(Long subscriptionId) {
        if (subscriptionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(subscriptionMapper.selectById(subscriptionId)).map(this::toDomain);
    }

    @Override
    public List<PushSubscription> findBySubject(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId
    ) {
        LambdaQueryWrapper<PushSubscriptionDO> query = subjectQuery(subjectType, subjectId)
            .orderByAsc(PushSubscriptionDO::getId);
        return subscriptionMapper.selectList(query).stream().map(this::toDomain).toList();
    }

    @Override
    public List<PushSubscription> findAll() {
        return subscriptionMapper.selectList(new LambdaQueryWrapper<PushSubscriptionDO>()
                .orderByAsc(PushSubscriptionDO::getId))
            .stream().map(this::toDomain).toList();
    }

    @Override
    public long countBySubject(PersonalAccessTokenSubjectType subjectType, Long subjectId) {
        return subscriptionMapper.selectCount(subjectQuery(subjectType, subjectId));
    }

    @Override
    public boolean updateStatus(
        Long subscriptionId,
        PushSubscriptionStatus status,
        String modifier,
        LocalDateTime gmtModified
    ) {
        return subscriptionMapper.update(null, new LambdaUpdateWrapper<PushSubscriptionDO>()
            .set(PushSubscriptionDO::getStatus, status.name())
            .set(PushSubscriptionDO::getModifier, modifier)
            .set(PushSubscriptionDO::getGmtModified, gmtModified)
            .eq(PushSubscriptionDO::getId, subscriptionId)) > 0;
    }

    @Override
    public boolean updateProvisioned(
        Long subscriptionId,
        Long accessTokenId,
        String mqQueue,
        String mqUsername,
        String mqPassword,
        String modifier,
        LocalDateTime gmtModified
    ) {
        return subscriptionMapper.update(null, new LambdaUpdateWrapper<PushSubscriptionDO>()
            .set(PushSubscriptionDO::getAccessTokenId, accessTokenId)
            .set(PushSubscriptionDO::getMqQueue, mqQueue)
            .set(PushSubscriptionDO::getMqUsername, mqUsername)
            .set(PushSubscriptionDO::getMqPassword, mqPassword)
            .set(PushSubscriptionDO::getModifier, modifier)
            .set(PushSubscriptionDO::getGmtModified, gmtModified)
            .eq(PushSubscriptionDO::getId, subscriptionId)) > 0;
    }

    @Override
    public boolean updateMqPassword(
        Long subscriptionId,
        String mqPassword,
        String modifier,
        LocalDateTime gmtModified
    ) {
        return subscriptionMapper.update(null, new LambdaUpdateWrapper<PushSubscriptionDO>()
            .set(PushSubscriptionDO::getMqPassword, mqPassword)
            .set(PushSubscriptionDO::getModifier, modifier)
            .set(PushSubscriptionDO::getGmtModified, gmtModified)
            .eq(PushSubscriptionDO::getId, subscriptionId)) > 0;
    }

    @Override
    public boolean delete(Long subscriptionId) {
        subscriptionRoleMapper.delete(new LambdaQueryWrapper<PushSubscriptionRoleDO>()
            .eq(PushSubscriptionRoleDO::getSubscriptionId, subscriptionId));
        return subscriptionMapper.deleteById(subscriptionId) > 0;
    }

    @Override
    public void replaceRoles(Long subscriptionId, List<Long> roleIds) {
        subscriptionRoleMapper.delete(new LambdaQueryWrapper<PushSubscriptionRoleDO>()
            .eq(PushSubscriptionRoleDO::getSubscriptionId, subscriptionId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : roleIds) {
            PushSubscriptionRoleDO relation = new PushSubscriptionRoleDO();
            relation.setSubscriptionId(subscriptionId);
            relation.setRoleId(roleId);
            relation.setGmtCreate(now);
            subscriptionRoleMapper.insert(relation);
        }
    }

    @Override
    public List<Long> findRoleIds(Long subscriptionId) {
        return subscriptionRoleMapper.selectList(new LambdaQueryWrapper<PushSubscriptionRoleDO>()
                .eq(PushSubscriptionRoleDO::getSubscriptionId, subscriptionId)
                .orderByAsc(PushSubscriptionRoleDO::getRoleId))
            .stream().map(PushSubscriptionRoleDO::getRoleId).toList();
    }

    @Override
    public Map<Long, Integer> countRolesBySubscriptionIds(List<Long> subscriptionIds) {
        if (subscriptionIds == null || subscriptionIds.isEmpty()) {
            return Map.of();
        }
        return subscriptionRoleMapper.selectList(new LambdaQueryWrapper<PushSubscriptionRoleDO>()
                .in(PushSubscriptionRoleDO::getSubscriptionId, subscriptionIds))
            .stream()
            .collect(Collectors.groupingBy(
                PushSubscriptionRoleDO::getSubscriptionId,
                Collectors.summingInt(item -> 1)));
    }

    private LambdaQueryWrapper<PushSubscriptionDO> subjectQuery(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId
    ) {
        LambdaQueryWrapper<PushSubscriptionDO> query = new LambdaQueryWrapper<PushSubscriptionDO>()
            .eq(PushSubscriptionDO::getSubjectType, subjectType.name());
        if (subjectId == null) {
            query.isNull(PushSubscriptionDO::getSubjectId);
        } else {
            query.eq(PushSubscriptionDO::getSubjectId, subjectId);
        }
        return query;
    }

    private PushSubscription toDomain(PushSubscriptionDO dataObject) {
        return PushSubscription.builder()
            .id(dataObject.getId())
            .name(dataObject.getName())
            .description(dataObject.getDescription())
            .subjectType(PersonalAccessTokenSubjectType.valueOf(dataObject.getSubjectType()))
            .subjectId(dataObject.getSubjectId())
            .status(PushSubscriptionStatus.valueOf(dataObject.getStatus()))
            .accessTokenId(dataObject.getAccessTokenId())
            .mqQueue(dataObject.getMqQueue())
            .mqUsername(dataObject.getMqUsername())
            .mqPassword(dataObject.getMqPassword())
            .creator(dataObject.getCreator())
            .modifier(dataObject.getModifier())
            .gmtCreate(dataObject.getGmtCreate())
            .gmtModified(dataObject.getGmtModified())
            .build();
    }

    private PushSubscriptionDO toDataObject(PushSubscription subscription) {
        PushSubscriptionDO dataObject = new PushSubscriptionDO();
        dataObject.setId(subscription.getId());
        dataObject.setName(subscription.getName());
        dataObject.setDescription(subscription.getDescription());
        dataObject.setSubjectType(subscription.getSubjectType().name());
        dataObject.setSubjectId(subscription.getSubjectId());
        dataObject.setStatus(subscription.getStatus().name());
        dataObject.setAccessTokenId(subscription.getAccessTokenId());
        dataObject.setMqQueue(subscription.getMqQueue());
        dataObject.setMqUsername(subscription.getMqUsername());
        dataObject.setMqPassword(subscription.getMqPassword());
        dataObject.setCreator(subscription.getCreator());
        dataObject.setModifier(subscription.getModifier());
        dataObject.setGmtCreate(subscription.getGmtCreate());
        dataObject.setGmtModified(subscription.getGmtModified());
        return dataObject;
    }
}
