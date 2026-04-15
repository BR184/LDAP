package com.company.idm.interfaces.auth;

import java.util.Set;

/**
 * 封装当前登录用户信息的响应数据。
 */
public record CurrentUserResponse(
    Long userId,
    String username,
    String realName,
    String email,
    String mobile,
    String deptCode,
    Set<String> roleCodes
) {
}

