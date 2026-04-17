package com.company.idm.interfaces.department;

/**
 * 封装部门详情响应结构。
 */
public record DepartmentResponse(
    Long id,
    String deptCode,
    String deptName,
    String parentDeptCode,
    String ancestorPath,
    Integer deptLevel,
    String sourceType,
    String externalId,
    String ldapDn,
    Integer status
) {
}
