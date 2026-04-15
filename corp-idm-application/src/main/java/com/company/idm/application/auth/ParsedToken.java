package com.company.idm.application.auth;

import java.time.Instant;
import java.util.Set;

public record ParsedToken(Long userId, String username, Integer tokenVersion, Set<String> roleCodes, Instant expiresAt) {
}

