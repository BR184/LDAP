package com.company.idm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定启动阶段环境校验相关配置项。
 */
@ConfigurationProperties(prefix = "app.startup-check")
public class StartupCheckProperties {

    private boolean enabled;
    private boolean verifyPlaceholderUser = true;
    private String placeholderUid = "placeholder";
    private int dbValidationTimeoutSeconds = 2;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isVerifyPlaceholderUser() {
        return verifyPlaceholderUser;
    }

    public void setVerifyPlaceholderUser(boolean verifyPlaceholderUser) {
        this.verifyPlaceholderUser = verifyPlaceholderUser;
    }

    public String getPlaceholderUid() {
        return placeholderUid;
    }

    public void setPlaceholderUid(String placeholderUid) {
        this.placeholderUid = placeholderUid;
    }

    public int getDbValidationTimeoutSeconds() {
        return dbValidationTimeoutSeconds;
    }

    public void setDbValidationTimeoutSeconds(int dbValidationTimeoutSeconds) {
        this.dbValidationTimeoutSeconds = dbValidationTimeoutSeconds;
    }
}
