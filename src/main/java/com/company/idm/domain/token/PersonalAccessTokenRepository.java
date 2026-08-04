package com.company.idm.domain.token;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PersonalAccessTokenRepository {

    PersonalAccessToken create(PersonalAccessToken token);

    Optional<PersonalAccessToken> findByTokenUid(String tokenUid);

    Optional<PersonalAccessToken> findByIdAndSubject(
        Long tokenId,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId
    );

    List<PersonalAccessToken> findBySubject(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        long offset,
        int limit
    );

    long countBySubject(PersonalAccessTokenSubjectType subjectType, Long subjectId);

    long countActiveBySubject(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        LocalDateTime now
    );

    boolean revoke(
        Long tokenId,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        LocalDateTime revokedAt,
        String modifier
    );

    boolean rotate(PersonalAccessToken token);

    boolean delete(Long tokenId, PersonalAccessTokenSubjectType subjectType, Long subjectId);

    boolean updateLastUsedIfBefore(
        Long tokenId,
        LocalDateTime usedAt,
        String sourceIp,
        LocalDateTime writeThreshold
    );
}
