package com.company.idm.infrastructure.security;

import com.company.idm.application.auth.BearerCredentialAuthenticator;
import com.company.idm.application.auth.ParsedToken;
import com.company.idm.application.auth.TokenService;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserAccessPolicy;
import com.company.idm.domain.user.UserRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtBearerCredentialAuthenticator implements BearerCredentialAuthenticator {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final UserAccessPolicy userAccessPolicy;

    @Override
    public Optional<AuthenticatedUser> authenticate(String credential, String sourceIp) {
        ParsedToken parsedToken = tokenService.parse(credential);
        User user = userRepository.findByUserId(parsedToken.userId()).orElse(null);
        if (user == null
            || user.getId() == null
            || !user.getId().equals(parsedToken.id())
            || !userAccessPolicy.canAuthenticate(user)
            || !Objects.equals(user.getTokenVersion(), parsedToken.tokenVersion())) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedUser(
            user.getId(),
            user.getUserId(),
            user.getTokenVersion(),
            user.getRoleCodes(),
            CredentialType.SESSION,
            null,
            Set.of()
        ));
    }
}
