package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.infrastructure.persistence.dataobject.RoleDO;
import com.company.idm.infrastructure.persistence.dataobject.RolePermissionDO;
import com.company.idm.infrastructure.persistence.mapper.RoleMapper;
import com.company.idm.infrastructure.persistence.mapper.RolePermissionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisRoleRepository implements RoleRepository {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public Optional<Role> findById(Long id) {
        return Optional.ofNullable(roleMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Role> findByCode(String roleCode) {
        RoleDO dataObject = roleMapper.selectOne(new LambdaQueryWrapper<RoleDO>()
            .eq(RoleDO::getRoleCode, roleCode));
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public List<Role> findAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getStatus, 1)
                .orderByAsc(RoleDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Role save(Role role) {
        RoleDO dataObject = toDataObject(role);
        if (dataObject.getId() == null) {
            dataObject.setGmtCreate(LocalDateTime.now());
            dataObject.setGmtModified(LocalDateTime.now());
            roleMapper.insert(dataObject);
        } else {
            dataObject.setGmtModified(LocalDateTime.now());
            roleMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId));
        for (Long permissionId : permissionIds) {
            RolePermissionDO relation = new RolePermissionDO();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setCreator("system");
            relation.setGmtCreate(LocalDateTime.now());
            rolePermissionMapper.insert(relation);
        }
    }

    private Role toDomain(RoleDO dataObject) {
        return Role.builder()
            .id(dataObject.getId())
            .roleCode(dataObject.getRoleCode())
            .roleName(dataObject.getRoleName())
            .status(dataObject.getStatus())
            .remark(dataObject.getRemark())
            .build();
    }

    private RoleDO toDataObject(Role role) {
        RoleDO dataObject = new RoleDO();
        dataObject.setId(role.getId());
        dataObject.setRoleCode(role.getRoleCode());
        dataObject.setRoleName(role.getRoleName());
        dataObject.setStatus(role.getStatus());
        dataObject.setRemark(role.getRemark());
        dataObject.setCreator("system");
        dataObject.setModifier("system");
        return dataObject;
    }
}

