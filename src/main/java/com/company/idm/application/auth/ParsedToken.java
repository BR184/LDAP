package com.company.idm.application.auth;

import java.time.Instant;
import java.util.Set;

/**
 * 封装解析 JWT 后得到的身份上下文信息。
 */
public record ParsedToken(Long id, String userId, Integer tokenVersion, Set<String> roleCodes, Instant expiresAt) {
    public String username() {
        return userId;
    }
}

