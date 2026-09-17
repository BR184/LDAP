package com.company.idm.domain.rolegroup;

import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.time.LocalDateTime;
import java.util.List;

public interface RoleMembershipChangeRepository {

    long currentCursor();

    List<RoleMembershipChange> findAfter(
        long cursor,
        int limit,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId
    );

    List<RoleMembershipChange> findUnpublished(int limit);

    void markPublished(List<Long> changeIds, LocalDateTime publishedAt);
}
