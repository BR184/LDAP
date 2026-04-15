package com.company.idm.application.auth;

import com.company.idm.domain.user.User;
import java.util.Set;

/**
 * 定义访问令牌的生成与解析能力。
 */
public interface TokenService {

    LoginResult generate(User user, Set<String> roleCodes);

    ParsedToken parse(String token);
}

