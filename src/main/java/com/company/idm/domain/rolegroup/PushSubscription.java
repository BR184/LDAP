package com.company.idm.domain.rolegroup;

import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 角色变更推送订阅：第三方在平台建立的订阅主体，持有订阅令牌与专属MQ队列。
 * 主体类型与订阅令牌保持一致：ROLE_GROUP（订阅本组角色集合）或 GLOBAL（平台管理员订阅全平台角色）。
 */
@Getter
@Builder(toBuilder = true)
public class PushSubscription {

    private final Long id;
    private final String name;
    private final String description;
    private final PersonalAccessTokenSubjectType subjectType;
    private final Long subjectId;
    private final PushSubscriptionStatus status;
    private final Long accessTokenId;
    private final String mqQueue;
    private final String mqUsername;
    private final String mqPassword;
    private final String creator;
    private final String modifier;
    private final LocalDateTime gmtCreate;
    private final LocalDateTime gmtModified;
}
