package com.company.idm.interfaces.user;

import jakarta.validation.constraints.NotNull;

/**
 * 封装用户状态变更接口的请求参数。
 */
public record UpdateUserStatusRequest(@NotNull(message = "状态不能为空") Integer statusCode) {
}

