package com.company.idm.application.token;

import com.company.idm.domain.token.PersonalAccessTokenScopeMode;
import java.time.LocalDateTime;
import java.util.List;

public record CreatePersonalAccessTokenCommand(
    String name,
    String description,
    LocalDateTime expiresAt,
    PersonalAccessTokenScopeMode scopeMode,
    List<Long> permissionIds,
    String sourceIp
) {
}
