package com.company.idm.infrastructure.security;

import com.company.idm.application.auth.BearerCredentialAuthenticator;
import com.company.idm.application.token.PersonalAccessTokenSecretService;
import com.company.idm.application.token.PersonalAccessTokenUsageService;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenPermission;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserAccessPolicy;
import com.company.idm.domain.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PersonalAccessTokenAuthenticator implements BearerCredentialAuthenticator {

    private final PersonalAccessTokenSecretService secretService;
    private final PersonalAccessTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UserAccessPolicy userAccessPolicy;
    private final PersonalAccessTokenUsageService usageService;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    @Autowired
    public PersonalAccessTokenAuthenticator(
        PersonalAccessTokenSecretService secretService,
        PersonalAccessTokenRepository tokenRepository,
        UserRepository userRepository,
        UserAccessPolicy userAccessPolicy,
        PersonalAccessTokenUsageService usageService,
        AuditLogRepository auditLogRepository
    ) {
        this(
            secretService,
            tokenRepository,
            userRepository,
            userAccessPolicy,
            usageService,
            auditLogRepository,
            Clock.systemDefaultZone()
        );
    }

    PersonalAccessTokenAuthenticator(
        PersonalAccessTokenSecretService secretService,
        PersonalAccessTokenRepository tokenRepository,
        UserRepository userRepository,
        UserAccessPolicy userAccessPolicy,
        PersonalAccessTokenUsageService usageService,
        AuditLogRepository auditLogRepository,
        Clock clock
    ) {
        this.secretService = secretService;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.userAccessPolicy = userAccessPolicy;
        this.usageService = usageService;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Override
    public Optional<AuthenticatedUser> authenticate(String credential, String sourceIp) {
        Optional<String> tokenUid = secretService.extractTokenUid(credential);
        if (tokenUid.isEmpty()) {
            auditFailure(null, null, sourceIp);
            return Optional.empty();
        }
        PersonalAccessToken token = tokenRepository.findByTokenUid(tokenUid.get()).orElse(null);
        if (token == null
            || token.getHashVersion() == null
            || !token.isActiveAt(LocalDateTime.now(clock))
            || !secretService.verify(credential, token.getSecretHash(), token.getHashVersion())) {
            auditFailure(token, tokenUid.get(), sourceIp);
            return Optional.empty();
        }
        User owner = userRepository.findById(token.getUserId()).orElse(null);
        if (!userAccessPolicy.canAuthenticate(owner)) {
            auditFailure(token, tokenUid.get(), sourceIp);
            return Optional.empty();
        }
        Set<String> selectedPermissionCodes = token.getPermissions().stream()
            .map(PersonalAccessTokenPermission::code)
            .collect(Collectors.toUnmodifiableSet());
        usageService.recordSuccessfulUse(token, sourceIp);
        return Optional.of(new AuthenticatedUser(
            owner.getId(),
            owner.getUserId(),
            owner.getTokenVersion(),
            owner.getRoleCodes(),
            CredentialType.PERSONAL_ACCESS_TOKEN,
            token.getId(),
            selectedPermissionCodes
        ));
    }

    private void auditFailure(PersonalAccessToken token, String tokenUid, String sourceIp) {
        auditLogRepository.save(AuditLog.builder()
            .operator("UNKNOWN")
            .operatorIp(sourceIp)
            .credentialType(CredentialType.PERSONAL_ACCESS_TOKEN.name())
            .credentialId(token == null ? null : token.getId())
            .operationType("PAT_AUTHENTICATION")
            .bizType("PERSONAL_ACCESS_TOKEN")
            .bizId(token == null ? tokenUid : String.valueOf(token.getId()))
            .result("FAIL")
            .errorMessage("INVALID_CREDENTIAL")
            .build());
    }
}
