package com.company.idm.domain.department;

import java.util.List;
import java.util.Optional;

/**
 * 定义部门领域仓储接口。
 */
public interface DepartmentRepository {

    Optional<Department> findByDeptCode(String deptCode);

    Optional<Department> findByExternalId(String externalId);

    List<Department> findAll();

    Department save(Department department);

    boolean existsChildren(String deptCode);

    void deleteByDeptCode(String deptCode);
}

