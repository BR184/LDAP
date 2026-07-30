package com.company.idm.interfaces.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserAccessRequest(
    @NotNull(message = "允许使用不能为空") Boolean accessAllowed
) {
}
