package com.company.idm.application.rolegroup;

import com.company.idm.domain.rolegroup.PushSubscriptionStatus;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.time.LocalDateTime;

public record SubscriptionView(
    Long id,
    String name,
    String description,
    PersonalAccessTokenSubjectType subjectType,
    Long subjectId,
    PushSubscriptionStatus status,
    int roleCount,
    String creator,
    LocalDateTime gmtCreate,
    LocalDateTime gmtModified
) {
}
