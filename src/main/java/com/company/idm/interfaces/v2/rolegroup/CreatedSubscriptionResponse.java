package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.CreatedSubscription;

public record CreatedSubscriptionResponse(
    SubscriptionResponse subscription,
    SubscriptionCredentialResponse credential
) {

    public static CreatedSubscriptionResponse from(CreatedSubscription created) {
        return new CreatedSubscriptionResponse(
            SubscriptionResponse.from(created.subscription()),
            SubscriptionCredentialResponse.from(created.credential())
        );
    }
}
