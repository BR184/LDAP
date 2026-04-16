package com.company.idm.interfaces.role;

import jakarta.validation.constraints.NotNull;

/**
 * 封装更新角色状态接口的请求参数。
 */
public record UpdateRoleStatusRequest(@NotNull(message = "状态不能为空") Integer status) {
}

