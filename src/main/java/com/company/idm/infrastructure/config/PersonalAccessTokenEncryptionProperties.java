package com.company.idm.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.personal-access-token.encryption")
public class PersonalAccessTokenEncryptionProperties {

    private String activeKeyId = "";
    private Map<String, String> keys = new LinkedHashMap<>();

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId == null ? "" : activeKeyId.trim();
    }

    public Map<String, String> getKeys() {
        return Map.copyOf(keys);
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keys);
    }
}
