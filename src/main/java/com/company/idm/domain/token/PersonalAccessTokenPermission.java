package com.company.idm.domain.token;

/**
 * Describes one immutable API permission selected for a personal access token.
 */
public record PersonalAccessTokenPermission(
    Long id,
    String code,
    String name,
    String resourcePath,
    String action
) {
}
