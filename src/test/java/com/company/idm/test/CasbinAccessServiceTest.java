package com.company.idm.test;

import com.company.idm.infrastructure.casbin.CasbinAccessService;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import java.util.List;
import java.util.Set;
import org.casbin.jcasbin.main.Enforcer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 验证 Casbin 访问控制服务的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class CasbinAccessServiceTest {

    @Mock
    private Enforcer enforcer;

    @InjectMocks
    private CasbinAccessService casbinAccessService;

    @Test
    void shouldReturnFalseWhenAuthenticationMissing() {
        assertThat(casbinAccessService.check(null, "/api/v1/users", "GET")).isFalse();
    }

    @Test
    void shouldDelegateToEnforcer() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "admin", 0, Set.of("ADMIN"));
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, List.of());
        when(enforcer.enforce("admin", "/api/v1/users", "GET")).thenReturn(true);

        assertThat(casbinAccessService.check(authentication, "/api/v1/users", "GET")).isTrue();
    }
}
