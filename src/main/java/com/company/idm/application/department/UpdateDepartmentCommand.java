package com.company.idm.application.department;

/**
 * 封装更新部门时的应用层命令参数。
 */
public record UpdateDepartmentCommand(
    String deptCode,
    String deptName,
    String parentDeptCode,
    Integer status
) {
}
