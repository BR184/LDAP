package com.company.idm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.personal-access-token")
public class PersonalAccessTokenProperties {

    private int maxActivePerUser = 20;
    private int lastUsedWriteIntervalSeconds = 300;

    public int getMaxActivePerUser() {
        return maxActivePerUser;
    }

    public void setMaxActivePerUser(int maxActivePerUser) {
        this.maxActivePerUser = maxActivePerUser;
    }

    public int getLastUsedWriteIntervalSeconds() {
        return lastUsedWriteIntervalSeconds;
    }

    public void setLastUsedWriteIntervalSeconds(int lastUsedWriteIntervalSeconds) {
        this.lastUsedWriteIntervalSeconds = lastUsedWriteIntervalSeconds;
    }
}
