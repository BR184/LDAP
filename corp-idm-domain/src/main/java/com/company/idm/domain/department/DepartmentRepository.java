package com.company.idm.domain.department;

import java.util.Optional;

public interface DepartmentRepository {

    Optional<Department> findByDeptCode(String deptCode);
}

