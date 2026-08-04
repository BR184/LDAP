package com.company.idm.interfaces.rolegroup;

import com.company.idm.domain.token.PersonalAccessToken;
import java.time.LocalDateTime;

public record RoleSupplyTokenResponse(
    Long id,
    String name,
    String description,
    String status,
    String subjectType,
    Long subjectId,
    LocalDateTime expiresAt,
    LocalDateTime lastUsedAt,
    String lastUsedIp,
    LocalDateTime createdAt,
    boolean secretRecoverable
) {
    public static RoleSupplyTokenResponse from(PersonalAccessToken token, LocalDateTime now) {
        return new RoleSupplyTokenResponse(
            token.getId(),
            token.getName(),
            token.getDescription(),
            resolveStatus(token, now),
            token.getSubjectType().name(),
            token.getSubjectId(),
            token.getExpiresAt(),
            token.getLastUsedAt(),
            token.getLastUsedIp(),
            token.getGmtCreate(),
            token.isSecretRecoverable()
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
