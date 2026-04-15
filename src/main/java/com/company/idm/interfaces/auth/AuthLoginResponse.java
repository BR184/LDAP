package com.company.idm.interfaces.auth;

import java.time.Instant;
import java.util.Set;

/**
 * 封装登录接口的响应数据。
 */
public record AuthLoginResponse(Long userId, String username, Set<String> roleCodes, String accessToken, Instant expiresAt) {
}

