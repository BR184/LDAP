package com.company.idm.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("credentialAccessService")
public class CredentialAccessService {

    public boolean isSession(Authentication authentication) {
        return authentication != null
            && authentication.getPrincipal() instanceof AuthenticatedUser principal
            && principal.credentialType() == CredentialType.SESSION;
    }

    public boolean isRoleSupplyToken(Authentication authentication) {
        return authentication != null
            && authentication.getPrincipal() instanceof AuthenticatedUser principal
            && principal.credentialType() == CredentialType.PERSONAL_ACCESS_TOKEN
            && principal.tokenSubjectType() != null
            && principal.tokenSubjectType() != com.company.idm.domain.token.PersonalAccessTokenSubjectType.USER;
    }
}
