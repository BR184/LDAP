package com.company.idm.domain.user;

/**
 * 定义用户密码规则校验能力。
 */
public interface PasswordPolicyValidator {

    void validate(String password);
}

