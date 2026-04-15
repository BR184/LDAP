package com.company.idm.application.auth;

import java.time.Instant;
import java.util.Set;

public record LoginResult(Long userId, String username, Set<String> roleCodes, String accessToken, Instant expiresAt) {
}

