package com.company.idm.application.department;

/**
 * 封装删除部门时的应用层命令参数。
 */
public record DeleteDepartmentCommand(String deptCode, String operator) {
}
