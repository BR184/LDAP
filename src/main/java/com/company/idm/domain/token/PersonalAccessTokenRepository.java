package com.company.idm.domain.token;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PersonalAccessTokenRepository {

    PersonalAccessToken create(PersonalAccessToken token);

    Optional<PersonalAccessToken> findByTokenUid(String tokenUid);

    Optional<PersonalAccessToken> findOwnedById(Long tokenId, Long userId);

    List<PersonalAccessToken> findByUserId(Long userId, long offset, int limit);

    long countByUserId(Long userId);

    long countActiveByUserId(Long userId, LocalDateTime now);

    boolean revokeOwned(Long tokenId, Long userId, LocalDateTime revokedAt, String modifier);

    boolean updateLastUsedIfBefore(
        Long tokenId,
        LocalDateTime usedAt,
        String sourceIp,
        LocalDateTime writeThreshold
    );
}
