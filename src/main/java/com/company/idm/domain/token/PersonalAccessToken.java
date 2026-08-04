package com.company.idm.domain.token;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Personal access token metadata and its immutable selected permission scope.
 */
@Getter
@Builder(toBuilder = true)
public class PersonalAccessToken {

    private final Long id;
    private final String tokenUid;
    private final Long userId;
    private final String name;
    private final String description;
    private final PersonalAccessTokenScopeMode scopeMode;
    private final String secretHash;
    private final String secretCiphertext;
    private final String secretKeyId;
    private final Integer hashVersion;
    private final String tokenPrefix;
    private final LocalDateTime expiresAt;
    private final LocalDateTime revokedAt;
    private final LocalDateTime lastUsedAt;
    private final String lastUsedIp;
    private final String creator;
    private final String modifier;
    private final LocalDateTime gmtCreate;
    private final LocalDateTime gmtModified;
    private final List<PersonalAccessTokenPermission> permissions;

    public List<PersonalAccessTokenPermission> getPermissions() {
        return permissions == null ? List.of() : List.copyOf(permissions);
    }

    public boolean isActiveAt(LocalDateTime now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public boolean isSecretRecoverable() {
        return secretCiphertext != null && !secretCiphertext.isBlank()
            && secretKeyId != null && !secretKeyId.isBlank();
    }
}
