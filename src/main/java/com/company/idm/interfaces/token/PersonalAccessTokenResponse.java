package com.company.idm.interfaces.token;

import com.company.idm.domain.token.PersonalAccessToken;
import java.time.LocalDateTime;
import java.util.List;

public record PersonalAccessTokenResponse(
    Long id,
    String name,
    String tokenPrefix,
    String status,
    LocalDateTime expiresAt,
    LocalDateTime lastUsedAt,
    String lastUsedIp,
    LocalDateTime createdAt,
    List<TokenPermissionResponse> permissions
) {

    public static PersonalAccessTokenResponse from(PersonalAccessToken token, LocalDateTime now) {
        return new PersonalAccessTokenResponse(
            token.getId(),
            token.getName(),
            token.getTokenPrefix(),
            resolveStatus(token, now),
            token.getExpiresAt(),
            token.getLastUsedAt(),
            token.getLastUsedIp(),
            token.getGmtCreate(),
            token.getPermissions().stream().map(TokenPermissionResponse::from).toList()
        );
    }

    private static String resolveStatus(PersonalAccessToken token, LocalDateTime now) {
        if (token.getRevokedAt() != null) {
            return "REVOKED";
        }
        if (token.getExpiresAt() != null && !token.getExpiresAt().isAfter(now)) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }
}
