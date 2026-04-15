package com.company.idm.domain.department;

import java.util.Optional;

/**
 * 定义部门领域仓储接口。
 */
public interface DepartmentRepository {

    Optional<Department> findByDeptCode(String deptCode);
}

