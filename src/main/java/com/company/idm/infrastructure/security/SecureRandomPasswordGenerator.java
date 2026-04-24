package com.company.idm.infrastructure.security;

import com.company.idm.domain.user.PasswordGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 基于安全随机数生成 6 位数字密码。
 */
@Component
public class SecureRandomPasswordGenerator implements PasswordGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateSixDigitNumericPassword() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }
}
