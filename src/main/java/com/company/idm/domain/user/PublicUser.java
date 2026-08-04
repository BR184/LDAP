package com.company.idm.domain.user;

/**
 * Minimal public identity projection used by delegated role membership management.
 */
public record PublicUser(Long id, String realName) {
}
