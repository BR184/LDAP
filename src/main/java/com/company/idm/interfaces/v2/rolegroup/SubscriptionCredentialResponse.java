package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.SubscriptionCredential;

public record SubscriptionCredentialResponse(
    Long subscriptionId,
    String tokenSecret,
    SubscriptionMqInfoResponse mq
) {

    public static SubscriptionCredentialResponse from(SubscriptionCredential credential) {
        return new SubscriptionCredentialResponse(
            credential.subscriptionId(),
            credential.tokenSecret(),
            SubscriptionMqInfoResponse.from(credential.mq())
        );
    }
}
