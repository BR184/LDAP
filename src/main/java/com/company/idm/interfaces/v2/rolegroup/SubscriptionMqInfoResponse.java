package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.SubscriptionMqInfo;

public record SubscriptionMqInfoResponse(
    String host,
    int port,
    String vhost,
    String queue,
    String username,
    String password
) {

    public static SubscriptionMqInfoResponse from(SubscriptionMqInfo info) {
        return new SubscriptionMqInfoResponse(
            info.host(),
            info.port(),
            info.vhost(),
            info.queue(),
            info.username(),
            info.password()
        );
    }
}
