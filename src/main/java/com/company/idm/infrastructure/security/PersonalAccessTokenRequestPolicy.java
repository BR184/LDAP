package com.company.idm.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class PersonalAccessTokenRequestPolicy {

    private static final String ROLE_SUPPLY_PATH_PREFIX = "/api/v1/open/role-supply";

    public boolean allows(AuthenticatedUser principal, String servletPath) {
        if (servletPath == null) {
            return false;
        }
        if (principal != null
            && principal.tokenSubjectType() != null
            && principal.tokenSubjectType() != com.company.idm.domain.token.PersonalAccessTokenSubjectType.USER) {
            return servletPath.equals(ROLE_SUPPLY_PATH_PREFIX)
                || servletPath.startsWith(ROLE_SUPPLY_PATH_PREFIX + "/");
        }
        return !servletPath.equals("/api/v1/users/me/password")
            && !servletPath.equals("/api/v1/users/me/password/verify")
            && !servletPath.equals("/api/v1/menus/self/tree")
            && !servletPath.equals("/api/v1/personal-access-tokens")
            && !servletPath.startsWith("/api/v1/personal-access-tokens/");
    }
}
