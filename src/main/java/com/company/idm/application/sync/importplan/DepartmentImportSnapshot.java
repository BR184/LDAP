package com.company.idm.application.sync.importplan;

import com.company.idm.common.enums.SourceType;
import com.company.idm.domain.department.Department;

public record DepartmentImportSnapshot(
    Long id,
    String deptCode,
    String deptName,
    String parentDeptCode,
    String ancestorPath,
    Integer deptLevel,
    SourceType sourceType,
    String externalId,
    String ldapDn,
    Integer status
) {

    public static DepartmentImportSnapshot from(Department department) {
        if (department == null) {
            return null;
        }
        return new DepartmentImportSnapshot(
            department.getId(),
            department.getDeptCode(),
            department.getDeptName(),
            department.getParentDeptCode(),
            department.getAncestorPath(),
            department.getDeptLevel(),
            department.getSourceType(),
            department.getExternalId(),
            department.getLdapDn(),
            department.getStatus()
        );
    }

    public Department toDepartment() {
        return Department.builder()
            .id(id)
            .deptCode(deptCode)
            .deptName(deptName)
            .parentDeptCode(parentDeptCode)
            .ancestorPath(ancestorPath)
            .deptLevel(deptLevel)
            .sourceType(sourceType)
            .externalId(externalId)
            .ldapDn(ldapDn)
            .status(status)
            .build();
    }
}
