package com.company.idm.application.token;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.infrastructure.config.PersonalAccessTokenProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PersonalAccessTokenUsageService {

    private final PersonalAccessTokenRepository tokenRepository;
    private final PersonalAccessTokenProperties properties;
    private final Clock clock;
    private final Cache<Long, LocalDateTime> nextWriteAtByTokenId;

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
        Duration reservationRetention = Duration.ofSeconds(
            Math.max(1, properties.getLastUsedWriteIntervalSeconds())
        );
        this.nextWriteAtByTokenId = Caffeine.newBuilder()
            .expireAfterWrite(reservationRetention)
            .build();
    }

    public void recordSuccessfulUse(PersonalAccessToken token, String sourceIp) {
        if (token.getId() == null) {
            return;
        }
        LocalDateTime usedAt = LocalDateTime.now(clock);
        int intervalSeconds = Math.max(0, properties.getLastUsedWriteIntervalSeconds());
        LocalDateTime writeThreshold = usedAt.minusSeconds(intervalSeconds);
        if (token.getLastUsedAt() != null && token.getLastUsedAt().isAfter(writeThreshold)) {
            return;
        }
        if (!reserveWrite(token.getId(), usedAt, intervalSeconds)) {
            return;
        }
        try {
            tokenRepository.updateLastUsedIfBefore(
                token.getId(),
                usedAt,
                normalizeIp(sourceIp),
                writeThreshold
            );
        } catch (RuntimeException exception) {
            nextWriteAtByTokenId.invalidate(token.getId());
            throw exception;
        }
    }

    private boolean reserveWrite(Long tokenId, LocalDateTime usedAt, int intervalSeconds) {
        AtomicBoolean reserved = new AtomicBoolean(false);
        nextWriteAtByTokenId.asMap().compute(tokenId, (ignored, nextWriteAt) -> {
            if (nextWriteAt != null && usedAt.isBefore(nextWriteAt)) {
                return nextWriteAt;
            }
            reserved.set(true);
            return usedAt.plusSeconds(intervalSeconds);
        });
        return reserved.get();
    }

    private String normalizeIp(String sourceIp) {
        return sourceIp == null || sourceIp.isBlank() ? "UNKNOWN" : sourceIp.trim();
    }
}
