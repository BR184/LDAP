package com.company.idm.application.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InitialPasswordPolicyTest {

    private final InitialPasswordPolicy policy = new InitialPasswordPolicy();

    @Test
    void usesValidMobileAsInitialPassword() {
        assertThat(policy.resolve("13800138000")).isEqualTo("13800138000");
    }

    @Test
    void trimsValidMobileBeforeUsingIt() {
        assertThat(policy.resolve(" 13800138000 ")).isEqualTo("13800138000");
    }

    @Test
    void fallsBackWhenMobileIsMissing() {
        assertThat(policy.resolve(null)).isEqualTo("123456");
        assertThat(policy.resolve(" ")).isEqualTo("123456");
    }

    @Test
    void fallsBackWhenMobileIsInvalid() {
        assertThat(policy.resolve("23800138000")).isEqualTo("123456");
        assertThat(policy.resolve("1380013800")).isEqualTo("123456");
        assertThat(policy.resolve("1380013800a")).isEqualTo("123456");
    }
}
