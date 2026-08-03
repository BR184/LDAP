package com.company.idm.application.token;

import java.time.LocalDateTime;
import java.util.List;

public record CreatePersonalAccessTokenCommand(
    String name,
    LocalDateTime expiresAt,
    List<Long> permissionIds,
    String passwordVerificationToken,
    String sourceIp
) {
}
