package com.company.idm.domain.rolegroup;

import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PushSubscriptionRepository {

    PushSubscription save(PushSubscription subscription);

    Optional<PushSubscription> findById(Long subscriptionId);

    List<PushSubscription> findBySubject(PersonalAccessTokenSubjectType subjectType, Long subjectId);

    List<PushSubscription> findAll();

    long countBySubject(PersonalAccessTokenSubjectType subjectType, Long subjectId);

    boolean updateStatus(
        Long subscriptionId,
        PushSubscriptionStatus status,
        String modifier,
        LocalDateTime gmtModified
    );

    boolean updateProvisioned(
        Long subscriptionId,
        Long accessTokenId,
        String mqQueue,
        String mqUsername,
        String mqPassword,
        String modifier,
        LocalDateTime gmtModified
    );

    boolean updateMqPassword(
        Long subscriptionId,
        String mqPassword,
        String modifier,
        LocalDateTime gmtModified
    );

    boolean delete(Long subscriptionId);

    void replaceRoles(Long subscriptionId, List<Long> roleIds);

    List<Long> findRoleIds(Long subscriptionId);

    Map<Long, Integer> countRolesBySubscriptionIds(List<Long> subscriptionIds);
}
