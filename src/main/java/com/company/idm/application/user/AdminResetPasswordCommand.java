package com.company.idm.application.user;

import java.util.Set;

/**
 * 封装管理员重置用户密码的请求参数。
 */
public record AdminResetPasswordCommand(
    Long userId,
    String operator,
    String operatorIp,
    Set<String> effectivePermissionCodes
) {

    public AdminResetPasswordCommand {
        effectivePermissionCodes = effectivePermissionCodes == null
            ? Set.of()
            : Set.copyOf(effectivePermissionCodes);
    }
}
