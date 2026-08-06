package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.persistence.dataobject.RoleDO;
import com.company.idm.infrastructure.persistence.dataobject.RolePermissionDO;
import com.company.idm.infrastructure.persistence.dataobject.UserRoleDO;
import com.company.idm.infrastructure.persistence.mapper.RoleMapper;
import com.company.idm.infrastructure.persistence.mapper.RolePermissionMapper;
import com.company.idm.infrastructure.persistence.mapper.UserRoleMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现角色仓储与授权关系维护。
 */
@Repository
@RequiredArgsConstructor
public class MybatisRoleRepository implements RoleRepository {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public Optional<Role> findById(Long id) {
        return Optional.ofNullable(roleMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Role> findByIdForUpdate(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(roleMapper.selectByIdForUpdate(id)).map(this::toDomain);
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
                .orderByAsc(RoleDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Role> findByCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                .in(RoleDO::getRoleCode, roleCodes)
                .eq(RoleDO::getStatus, 1))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Role> findByIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Role> findByScope(RoleScope roleScope) {
        if (roleScope == null) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getRoleScope, roleScope.name())
                .eq(RoleDO::getStatus, 1)
                .orderByAsc(RoleDO::getRoleName)
                .orderByAsc(RoleDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Role> findByGroupId(Long roleGroupId) {
        if (roleGroupId == null) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getRoleScope, RoleScope.GROUP.name())
                .eq(RoleDO::getRoleGroupId, roleGroupId)
                .orderByAsc(RoleDO::getRoleName)
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
    public void updateStatus(Long id, Integer status) {
        RoleDO dataObject = new RoleDO();
        dataObject.setId(id);
        dataObject.setStatus(status);
        dataObject.setModifier("system");
        dataObject.setGmtModified(LocalDateTime.now());
        roleMapper.updateById(dataObject);
    }

    @Override
    public void delete(Long id) {
        roleMapper.deleteById(id);
    }

    @Override
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        RoleDO role = roleMapper.selectByIdForUpdate(roleId);
        if (role != null
            && RoleScope.GROUP.name().equals(role.getRoleScope())
            && permissionIds != null
            && !permissionIds.isEmpty()) {
            throw new BizException("GROUP_ROLE_PERMISSION_FORBIDDEN", "组角色不能绑定平台权限");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId));
        for (Long permissionId : permissionIds == null ? List.<Long>of() : permissionIds) {
            RolePermissionDO relation = new RolePermissionDO();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setCreator("system");
            relation.setGmtCreate(LocalDateTime.now());
            rolePermissionMapper.insert(relation);
        }
    }

    @Override
    public void grantPermissionToRoles(List<Long> roleIds, Long permissionId) {
        if (roleIds == null || roleIds.isEmpty() || permissionId == null) {
            return;
        }
        for (Long roleId : roleIds) {
            RoleDO role = roleMapper.selectByIdForUpdate(roleId);
            if (role != null && RoleScope.GROUP.name().equals(role.getRoleScope())) {
                continue;
            }
            long existingCount = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermissionDO>()
                .eq(RolePermissionDO::getRoleId, roleId)
                .eq(RolePermissionDO::getPermissionId, permissionId));
            if (existingCount > 0) {
                continue;
            }
            RolePermissionDO relation = new RolePermissionDO();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setCreator("system");
            relation.setGmtCreate(LocalDateTime.now());
            rolePermissionMapper.insert(relation);
        }
    }

    @Override
    public void removePermissionFromAllRoles(Long permissionId) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionDO>()
            .eq(RolePermissionDO::getPermissionId, permissionId));
    }

    @Override
    public List<Long> findPermissionIdsByRoleId(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermissionDO>()
                .eq(RolePermissionDO::getRoleId, roleId))
            .stream()
            .map(RolePermissionDO::getPermissionId)
            .sorted()
            .toList();
    }

    @Override
    public boolean existsUserBinding(Long roleId) {
        return userRoleMapper.countActiveBindingsByRoleId(roleId) > 0;
    }

    private Role toDomain(RoleDO dataObject) {
        return Role.builder()
            .id(dataObject.getId())
            .roleCode(dataObject.getRoleCode())
            .roleName(dataObject.getRoleName())
            .permissionLevel(dataObject.getPermissionLevel())
            .builtIn(dataObject.getBuiltIn())
            .status(dataObject.getStatus())
            .remark(dataObject.getRemark())
            .roleScope(dataObject.getRoleScope() == null ? RoleScope.SYSTEM : RoleScope.valueOf(dataObject.getRoleScope()))
            .roleGroupId(dataObject.getRoleGroupId())
            .build();
    }

    private RoleDO toDataObject(Role role) {
        RoleDO dataObject = new RoleDO();
        dataObject.setId(role.getId());
        dataObject.setRoleCode(role.getRoleCode());
        dataObject.setRoleName(role.getRoleName());
        dataObject.setPermissionLevel(role.getPermissionLevel());
        dataObject.setBuiltIn(role.getBuiltIn());
        dataObject.setStatus(role.getStatus());
        dataObject.setRemark(role.getRemark());
        dataObject.setRoleScope((role.getRoleScope() == null ? RoleScope.SYSTEM : role.getRoleScope()).name());
        dataObject.setRoleGroupId(role.getRoleGroupId());
        dataObject.setCreator("system");
        dataObject.setModifier("system");
        return dataObject;
    }
}

