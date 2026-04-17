package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.SourceType;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.infrastructure.persistence.dataobject.DepartmentDO;
import com.company.idm.infrastructure.persistence.mapper.DepartmentMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现部门仓储。
 */
@Repository
@RequiredArgsConstructor
public class MybatisDepartmentRepository implements DepartmentRepository {

    private final DepartmentMapper departmentMapper;

    @Override
    public Optional<Department> findByDeptCode(String deptCode) {
        DepartmentDO dataObject = departmentMapper.selectOne(new LambdaQueryWrapper<DepartmentDO>()
            .eq(DepartmentDO::getDeptCode, deptCode));
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public Optional<Department> findByExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        DepartmentDO dataObject = departmentMapper.selectOne(new LambdaQueryWrapper<DepartmentDO>()
            .eq(DepartmentDO::getExternalId, externalId));
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public List<Department> findAll() {
        return departmentMapper.selectList(new LambdaQueryWrapper<DepartmentDO>()
                .orderByAsc(DepartmentDO::getDeptLevel, DepartmentDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Department save(Department department) {
        DepartmentDO dataObject = toDataObject(department);
        if (dataObject.getId() == null) {
            dataObject.setGmtCreate(LocalDateTime.now());
            dataObject.setGmtModified(LocalDateTime.now());
            departmentMapper.insert(dataObject);
        } else {
            dataObject.setGmtModified(LocalDateTime.now());
            departmentMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public boolean existsChildren(String deptCode) {
        return departmentMapper.selectCount(new LambdaQueryWrapper<DepartmentDO>()
            .eq(DepartmentDO::getParentDeptCode, deptCode)) > 0;
    }

    @Override
    public void deleteByDeptCode(String deptCode) {
        departmentMapper.delete(new LambdaQueryWrapper<DepartmentDO>()
            .eq(DepartmentDO::getDeptCode, deptCode));
    }

    private Department toDomain(DepartmentDO dataObject) {
        return Department.builder()
            .id(dataObject.getId())
            .deptCode(dataObject.getDeptCode())
            .deptName(dataObject.getDeptName())
            .parentDeptCode(dataObject.getParentDeptCode())
            .ancestorPath(dataObject.getAncestorPath())
            .deptLevel(dataObject.getDeptLevel())
            .sourceType(SourceType.valueOf(dataObject.getSourceType()))
            .externalId(dataObject.getExternalId())
            .ldapDn(dataObject.getLdapDn())
            .status(dataObject.getStatus())
            .build();
    }

    private DepartmentDO toDataObject(Department department) {
        DepartmentDO dataObject = new DepartmentDO();
        dataObject.setId(department.getId());
        dataObject.setDeptCode(department.getDeptCode());
        dataObject.setDeptName(department.getDeptName());
        dataObject.setParentDeptCode(department.getParentDeptCode());
        dataObject.setAncestorPath(department.getAncestorPath());
        dataObject.setDeptLevel(department.getDeptLevel());
        dataObject.setSourceType(department.getSourceType().name());
        dataObject.setExternalId(department.getExternalId());
        dataObject.setLdapDn(department.getLdapDn());
        dataObject.setStatus(department.getStatus());
        return dataObject;
    }
}

