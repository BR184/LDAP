package com.company.idm.application.department;

/**
 * 封装创建部门时的应用层命令参数。
 */
public record CreateDepartmentCommand(
    String deptCode,
    String deptName,
    String parentDeptCode,
    String externalId
) {
}
