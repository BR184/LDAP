package com.company.idm.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.Set;
import com.company.idm.domain.token.PersonalAccessTokenScopeMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class BearerAuthenticationFilterTest {

    private final JwtBearerCredentialAuthenticator jwtAuthenticator = mock(JwtBearerCredentialAuthenticator.class);
    private final PersonalAccessTokenAuthenticator patAuthenticator = mock(PersonalAccessTokenAuthenticator.class);
    private final BearerAuthenticationFilter filter = new BearerAuthenticationFilter(
        jwtAuthenticator,
        patAuthenticator,
        new PersonalAccessTokenRequestPolicy()
    );

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preservesJwtAuthenticationDispatch() throws Exception {
        AuthenticatedUser principal = principal(CredentialType.SESSION);
        when(jwtAuthenticator.authenticate("jwt-value", "127.0.0.1")).thenReturn(Optional.of(principal));
        MockHttpServletRequest request = request("/api/v1/auth/me", "Bearer jwt-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
        verify(jwtAuthenticator).authenticate("jwt-value", "127.0.0.1");
        verify(patAuthenticator, never()).authenticate("jwt-value", "127.0.0.1");
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsPatOnInteractiveOnlyEndpoints() throws Exception {
        MockHttpServletRequest request = request(
            "/api/v1/personal-access-tokens",
            "Bearer idm_pat_token"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(patAuthenticator, never()).authenticate(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        );
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest request(String servletPath, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(servletPath);
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        return request;
    }

    private AuthenticatedUser principal(CredentialType type) {
        return new AuthenticatedUser(1L, "employee", 0, Set.of(), type, null, Set.of(),
            type == CredentialType.PERSONAL_ACCESS_TOKEN ? PersonalAccessTokenScopeMode.FIXED : null);
    }
}
