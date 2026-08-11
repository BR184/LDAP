package com.company.idm.infrastructure.security;

import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import org.springframework.stereotype.Component;

/**
 * 个人访问令牌（PAT）的请求路径策略。
 * 同时覆盖 V1（/api/v1）与 V2（/api/v2）路径。
 */
@Component
public class PersonalAccessTokenRequestPolicy {

    private static final String[] ROLE_SUPPLY_PATH_PREFIXES = {
        "/api/v1/open/role-supply",
        "/api/v2/open/role-supply"
    };

    private static final String[] SELF_PASSWORD_PATHS = {
        "/api/v1/users/me/password",
        "/api/v2/users/me/password",
        "/api/v1/users/me/password/verify",
        "/api/v2/users/me/password/verify"
    };

    private static final String[] MENU_SELF_TREE_PATHS = {
        "/api/v1/menus/self/tree",
        "/api/v2/menus/self/tree"
    };

    private static final String[] PAT_MANAGEMENT_PREFIXES = {
        "/api/v1/personal-access-tokens",
        "/api/v2/personal-access-tokens"
    };

    public boolean allows(AuthenticatedUser principal, String servletPath) {
        if (servletPath == null) {
            return false;
        }
        if (principal != null
            && principal.tokenSubjectType() != null
            && principal.tokenSubjectType() != PersonalAccessTokenSubjectType.USER) {
            return matchesAnyPrefix(servletPath, ROLE_SUPPLY_PATH_PREFIXES);
        }
        return !equalsAny(servletPath, SELF_PASSWORD_PATHS)
            && !equalsAny(servletPath, MENU_SELF_TREE_PATHS)
            && !matchesAnyPrefix(servletPath, PAT_MANAGEMENT_PREFIXES);
    }

    private boolean matchesAnyPrefix(String path, String[] prefixes) {
        for (String prefix : prefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean equalsAny(String path, String[] candidates) {
        for (String candidate : candidates) {
            if (path.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
}
