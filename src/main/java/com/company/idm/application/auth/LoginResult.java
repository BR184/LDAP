package com.company.idm.application.auth;

import java.time.Instant;
import java.util.Set;

/**
 * 封装登录成功后返回的用户、角色与访问令牌信息。
 */
public record LoginResult(Long userId, String username, Set<String> roleCodes, String accessToken, Instant expiresAt) {
}

