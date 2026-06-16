package com.company.idm.interfaces.user;

/**
 * 当前用户旧密码校验通过后的短时凭证。
 */
public record VerifyPasswordResponse(String verificationToken) {
}
