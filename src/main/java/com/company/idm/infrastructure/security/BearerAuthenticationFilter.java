package com.company.idm.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import com.company.idm.infrastructure.util.ClientIpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class BearerAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PERSONAL_ACCESS_TOKEN_PREFIX = "idm_pat_";

    private final JwtBearerCredentialAuthenticator jwtAuthenticator;
    private final PersonalAccessTokenAuthenticator personalAccessTokenAuthenticator;
    private final PersonalAccessTokenRequestPolicy personalAccessTokenRequestPolicy;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String credential = authorization.substring(BEARER_PREFIX.length());
        try {
            Optional<AuthenticatedUser> principal = authenticate(
                credential,
                request.getServletPath(),
                ClientIpUtil.getClientIp(request)
            );
            principal.ifPresent(authenticatedUser -> establishSecurityContext(authenticatedUser, request));
        } catch (RuntimeException ignored) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private Optional<AuthenticatedUser> authenticate(String credential, String servletPath, String sourceIp) {
        if (credential.startsWith(PERSONAL_ACCESS_TOKEN_PREFIX)) {
            Optional<AuthenticatedUser> principal = personalAccessTokenAuthenticator.authenticate(credential, sourceIp);
            return principal.filter(authenticated -> personalAccessTokenRequestPolicy.allows(authenticated, servletPath));
        }
        return jwtAuthenticator.authenticate(credential, sourceIp);
    }

    private void establishSecurityContext(AuthenticatedUser principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
