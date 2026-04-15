package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.SourceType;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.infrastructure.persistence.dataobject.DepartmentDO;
import com.company.idm.infrastructure.persistence.mapper.DepartmentMapper;
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

    private Department toDomain(DepartmentDO dataObject) {
        return Department.builder()
            .id(dataObject.getId())
            .deptCode(dataObject.getDeptCode())
            .deptName(dataObject.getDeptName())
            .parentDeptCode(dataObject.getParentDeptCode())
            .sourceType(SourceType.valueOf(dataObject.getSourceType()))
            .externalId(dataObject.getExternalId())
            .status(dataObject.getStatus())
            .build();
    }
}

