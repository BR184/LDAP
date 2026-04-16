package com.company.idm.interfaces.role;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 封装更新角色接口的请求参数。
 */
public record UpdateRoleRequest(
    @NotBlank(message = "角色名称不能为空") String roleName,
    @NotNull(message = "权限等级不能为空") @Min(value = 1, message = "权限等级最小为1") Integer permissionLevel,
    String remark
) {
}

