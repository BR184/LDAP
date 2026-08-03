package com.company.idm.application.token;

import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.infrastructure.config.PersonalAccessTokenProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PersonalAccessTokenUsageService {

    private final PersonalAccessTokenRepository tokenRepository;
    private final PersonalAccessTokenProperties properties;
    private final Clock clock;

    @Autowired
    public PersonalAccessTokenUsageService(
        PersonalAccessTokenRepository tokenRepository,
        PersonalAccessTokenProperties properties
    ) {
        this(tokenRepository, properties, Clock.systemDefaultZone());
    }

    PersonalAccessTokenUsageService(
        PersonalAccessTokenRepository tokenRepository,
        PersonalAccessTokenProperties properties,
        Clock clock
    ) {
        this.tokenRepository = tokenRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public void recordSuccessfulUse(PersonalAccessToken token, String sourceIp) {
        LocalDateTime usedAt = LocalDateTime.now(clock);
        int intervalSeconds = Math.max(0, properties.getLastUsedWriteIntervalSeconds());
        LocalDateTime writeThreshold = usedAt.minusSeconds(intervalSeconds);
        if (token.getLastUsedAt() != null && token.getLastUsedAt().isAfter(writeThreshold)) {
            return;
        }
        tokenRepository.updateLastUsedIfBefore(
            token.getId(),
            usedAt,
            normalizeIp(sourceIp),
            writeThreshold
        );
    }

    private String normalizeIp(String sourceIp) {
        return sourceIp == null || sourceIp.isBlank() ? "UNKNOWN" : sourceIp.trim();
    }
}
