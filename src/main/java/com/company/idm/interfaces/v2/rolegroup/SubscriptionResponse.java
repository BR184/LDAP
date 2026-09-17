package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.SubscriptionView;
import java.time.LocalDateTime;

public record SubscriptionResponse(
    Long id,
    String name,
    String description,
    String subjectType,
    Long subjectId,
    String status,
    int roleCount,
    String creator,
    LocalDateTime gmtCreate,
    LocalDateTime gmtModified
) {

    public static SubscriptionResponse from(SubscriptionView view) {
        return new SubscriptionResponse(
            view.id(),
            view.name(),
            view.description(),
            view.subjectType() == null ? null : view.subjectType().name(),
            view.subjectId(),
            view.status() == null ? null : view.status().name(),
            view.roleCount(),
            view.creator(),
            view.gmtCreate(),
            view.gmtModified()
        );
    }
}
