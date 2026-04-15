package com.company.idm.infrastructure.security;

import java.util.Set;

public record AuthenticatedUser(Long userId, String username, Integer tokenVersion, Set<String> roleCodes) {
}

