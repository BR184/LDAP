package com.company.idm.test;

import com.company.idm.application.auth.ParsedToken;
import com.company.idm.application.auth.TokenService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.JwtAuthenticationFilter;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 验证 JWT 认证过滤器行为的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipWhenAuthorizationHeaderMissing() throws Exception {
        jwtAuthenticationFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldSetAuthenticationWhenTokenValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        User user = User.builder()
            .id(1L)
            .username("admin")
            .realName("管理员")
            .status(UserStatus.ENABLED)
            .sourceType(SourceType.MANUAL)
            .tokenVersion(2)
            .roleCodes(Set.of("SUPER_ADMIN"))
            .build();

        when(tokenService.parse("token")).thenReturn(new ParsedToken(1L, "admin", 2, Set.of("SUPER_ADMIN"), java.time.Instant.now()));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        jwtAuthenticationFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        assertThat(((AuthenticatedUser) authentication.getPrincipal()).username()).isEqualTo("admin");
    }
}

