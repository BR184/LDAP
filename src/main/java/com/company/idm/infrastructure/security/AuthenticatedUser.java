package com.company.idm.infrastructure.security;

import java.util.Set;

/**
 * 表示通过认证后放入安全上下文的用户主体。
 */
public record AuthenticatedUser(Long userId, String username, Integer tokenVersion, Set<String> roleCodes) {
}

