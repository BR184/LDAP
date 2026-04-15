package com.company.idm.interfaces.auth;

import java.time.Instant;
import java.util.Set;

public record AuthLoginResponse(Long userId, String username, Set<String> roleCodes, String accessToken, Instant expiresAt) {
}

