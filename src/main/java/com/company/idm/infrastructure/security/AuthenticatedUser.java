package com.company.idm.infrastructure.security;

import com.company.idm.domain.token.PersonalAccessTokenScopeMode;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.util.Set;

/**
 * 表示通过认证后放入安全上下文的用户主体。
 */
public record AuthenticatedUser(
    Long id,
    String userId,
    Integer tokenVersion,
    Set<String> roleCodes,
    CredentialType credentialType,
    Long credentialId,
    Set<String> selectedPermissionCodes,
    PersonalAccessTokenScopeMode personalAccessTokenScopeMode,
    PersonalAccessTokenSubjectType tokenSubjectType,
    Long tokenSubjectId
) {
}

