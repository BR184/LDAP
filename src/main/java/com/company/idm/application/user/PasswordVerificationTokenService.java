package com.company.idm.application.user;

import com.company.idm.domain.user.User;

/**
 * 签发和校验当前用户已通过旧密码验证的短时凭证。
 */
public interface PasswordVerificationTokenService {

    String generate(User user);

    void verify(String token, User user);
}
