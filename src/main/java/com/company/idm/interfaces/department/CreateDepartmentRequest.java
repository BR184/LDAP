package com.company.idm.interfaces.department;

import jakarta.validation.constraints.NotBlank;

/**
 * 封装创建部门接口的请求参数。
 */
public record CreateDepartmentRequest(
    @NotBlank(message = "部门编码不能为空") String deptCode,
    @NotBlank(message = "部门名称不能为空") String deptName,
    String parentDeptCode,
    String externalId
) {
}
