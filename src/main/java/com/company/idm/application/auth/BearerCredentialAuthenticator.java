package com.company.idm.application.auth;

import com.company.idm.infrastructure.security.AuthenticatedUser;
import java.util.Optional;

public interface BearerCredentialAuthenticator {

    Optional<AuthenticatedUser> authenticate(String credential, String sourceIp);
}
