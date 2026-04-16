package com.company.idm.infrastructure.security;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.user.PasswordPolicyValidator;
import org.springframework.stereotype.Component;

/**
 * 提供当前原型使用的最小密码规则校验实现。
 */
@Component
public class SimplePasswordPolicyValidator implements PasswordPolicyValidator {

    private static final int MIN_PASSWORD_LENGTH = 6;

    @Override
    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BizException("PASSWORD_POLICY_INVALID", "密码不能为空");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new BizException("PASSWORD_POLICY_INVALID", "密码长度不能少于6位");
        }
    }
}

