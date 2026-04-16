package com.company.idm.test;

import com.company.idm.application.auth.LoginResult;
import com.company.idm.application.auth.ParsedToken;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.config.JwtProperties;
import com.company.idm.infrastructure.security.JwtTokenService;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 JWT 令牌生成与解析的单元测试。
 */
class JwtTokenServiceTest {

    @Test
    void shouldGenerateAndParseToken() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("prototype-secret-prototype-secret-prototype-secret");
        jwtProperties.setExpireMinutes(30);
        JwtTokenService jwtTokenService = new JwtTokenService(jwtProperties);
        User user = User.builder()
            .id(1L)
            .username("admin")
            .realName("管理员")
            .status(UserStatus.ENABLED)
            .sourceType(SourceType.MANUAL)
            .tokenVersion(3)
            .roleCodes(Set.of("ADMIN"))
            .build();

        LoginResult result = jwtTokenService.generate(user, Set.of("ADMIN"));
        ParsedToken parsedToken = jwtTokenService.parse(result.accessToken());

        assertThat(parsedToken.userId()).isEqualTo(1L);
        assertThat(parsedToken.username()).isEqualTo("admin");
        assertThat(parsedToken.tokenVersion()).isEqualTo(3);
        assertThat(parsedToken.roleCodes()).containsExactly("ADMIN");
    }
}
