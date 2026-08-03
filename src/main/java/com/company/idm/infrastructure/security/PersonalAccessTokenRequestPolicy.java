package com.company.idm.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class PersonalAccessTokenRequestPolicy {

    public boolean allows(String servletPath) {
        if (servletPath == null) {
            return false;
        }
        return !servletPath.equals("/api/v1/users/me/password")
            && !servletPath.equals("/api/v1/users/me/password/verify")
            && !servletPath.equals("/api/v1/menus/self/tree")
            && !servletPath.equals("/api/v1/personal-access-tokens")
            && !servletPath.startsWith("/api/v1/personal-access-tokens/");
    }
}
