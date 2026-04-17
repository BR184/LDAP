package com.company.idm.interfaces.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 封装更新部门接口的请求参数。
 */
public record UpdateDepartmentRequest(
    @NotBlank(message = "部门名称不能为空") String deptName,
    String parentDeptCode,
    @NotNull(message = "部门状态不能为空") Integer status
) {
}
