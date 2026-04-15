package com.company.idm.interfaces.role;

import jakarta.validation.constraints.NotBlank;

/**
 * 封装创建角色接口的请求参数。
 */
public record CreateRoleRequest(
    @NotBlank(message = "角色编码不能为空") String roleCode,
    @NotBlank(message = "角色名称不能为空") String roleName,
    String remark
) {
}

