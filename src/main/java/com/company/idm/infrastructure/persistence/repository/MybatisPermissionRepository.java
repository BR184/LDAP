package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.PermissionType;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.RolePolicy;
import com.company.idm.infrastructure.persistence.dataobject.PermissionDO;
import com.company.idm.infrastructure.persistence.mapper.PermissionMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现权限仓储与策略查询。
 */
@Repository
@RequiredArgsConstructor
public class MybatisPermissionRepository implements PermissionRepository {

    private final PermissionMapper permissionMapper;

    @Override
    public List<Permission> findAll() {
        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionDO>()
                .eq(PermissionDO::getStatus, 1)
                .orderByAsc(PermissionDO::getSortNo))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<RolePolicy> listRolePolicies() {
        return permissionMapper.selectRolePolicies().stream()
            .map(item -> new RolePolicy(item.roleCode(), item.resourcePath(), item.action()))
            .toList();
    }

    @Override
    public Optional<Permission> findById(Long id) {
        return Optional.ofNullable(permissionMapper.selectById(id)).map(this::toDomain);
    }

    private Permission toDomain(PermissionDO dataObject) {
        return Permission.builder()
            .id(dataObject.getId())
            .permissionCode(dataObject.getPermissionCode())
            .permissionName(dataObject.getPermissionName())
            .permissionType(PermissionType.valueOf(dataObject.getPermissionType()))
            .resourcePath(dataObject.getResourcePath())
            .action(dataObject.getAction())
            .parentId(dataObject.getParentId())
            .sortNo(dataObject.getSortNo())
            .status(dataObject.getStatus())
            .build();
    }
}
