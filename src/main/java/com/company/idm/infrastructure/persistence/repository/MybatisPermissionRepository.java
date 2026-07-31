package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.PermissionType;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.RolePolicy;
import com.company.idm.infrastructure.persistence.dataobject.PermissionDO;
import com.company.idm.infrastructure.persistence.mapper.PermissionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    @Override
    public Optional<Permission> findByCode(String permissionCode) {
        return Optional.ofNullable(permissionMapper.selectOne(new LambdaQueryWrapper<PermissionDO>()
            .eq(PermissionDO::getPermissionCode, permissionCode)))
            .map(this::toDomain);
    }

    @Override
    public Permission save(Permission permission) {
        PermissionDO dataObject = toDataObject(permission);
        if (dataObject.getId() == null) {
            dataObject.setGmtCreate(LocalDateTime.now());
            dataObject.setGmtModified(LocalDateTime.now());
            permissionMapper.insert(dataObject);
        } else {
            dataObject.setGmtModified(LocalDateTime.now());
            permissionMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public void delete(Long permissionId) {
        permissionMapper.deleteById(permissionId);
    }

    @Override
    public Set<String> findPermissionCodesByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Set.of();
        }
        List<String> permissionCodes = permissionMapper.selectPermissionCodesByUserId(userId);
        return permissionCodes == null || permissionCodes.isEmpty() ? Set.of() : Set.copyOf(permissionCodes);
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
            .remark(dataObject.getRemark())
            .build();
    }

    private PermissionDO toDataObject(Permission permission) {
        PermissionDO dataObject = new PermissionDO();
        dataObject.setId(permission.getId());
        dataObject.setPermissionCode(permission.getPermissionCode());
        dataObject.setPermissionName(permission.getPermissionName());
        dataObject.setPermissionType(permission.getPermissionType().name());
        dataObject.setResourcePath(permission.getResourcePath());
        dataObject.setAction(permission.getAction());
        dataObject.setParentId(permission.getParentId());
        dataObject.setSortNo(permission.getSortNo());
        dataObject.setStatus(permission.getStatus());
        dataObject.setRemark(permission.getRemark());
        return dataObject;
    }
}
