package com.company.idm.interfaces.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull(message = "状态不能为空") Integer statusCode) {
}

