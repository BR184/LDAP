package com.company.idm.test;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.security.SimplePasswordPolicyValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证最小密码规则校验器的单元测试。
 */
class SimplePasswordPolicyValidatorTest {

    private final SimplePasswordPolicyValidator validator = new SimplePasswordPolicyValidator();

    @Test
    void shouldAcceptPasswordWithAtLeastSixCharacters() {
        assertThatCode(() -> validator.validate("123456")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("abcdef")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectBlankOrTooShortPassword() {
        assertThatThrownBy(() -> validator.validate(""))
            .isInstanceOf(BizException.class)
            .hasMessage("密码不能为空");

        assertThatThrownBy(() -> validator.validate("12345"))
            .isInstanceOf(BizException.class)
            .hasMessage("密码长度不能少于6位");
    }
}

