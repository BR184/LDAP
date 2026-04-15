package com.company.idm.infrastructure.sql;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 SQL 日志能力的配置项。
 */
@ConfigurationProperties(prefix = "app.sql-log")
public class SqlLogProperties {

    private boolean enabled = false;
    private long slowSqlThresholdMs = 300;
    private boolean showParameters = true;
    private int maxSqlLength = 2000;
    private int maxParameterLength = 128;
    private int maxCollectionLength = 5;
    private List<String> maskKeywords = List.of("password", "pwd", "secret", "token", "credential", "bindPassword");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getSlowSqlThresholdMs() {
        return slowSqlThresholdMs;
    }

    public void setSlowSqlThresholdMs(long slowSqlThresholdMs) {
        this.slowSqlThresholdMs = slowSqlThresholdMs;
    }

    public boolean isShowParameters() {
        return showParameters;
    }

    public void setShowParameters(boolean showParameters) {
        this.showParameters = showParameters;
    }

    public int getMaxSqlLength() {
        return maxSqlLength;
    }

    public void setMaxSqlLength(int maxSqlLength) {
        this.maxSqlLength = maxSqlLength;
    }

    public int getMaxParameterLength() {
        return maxParameterLength;
    }

    public void setMaxParameterLength(int maxParameterLength) {
        this.maxParameterLength = maxParameterLength;
    }

    public int getMaxCollectionLength() {
        return maxCollectionLength;
    }

    public void setMaxCollectionLength(int maxCollectionLength) {
        this.maxCollectionLength = maxCollectionLength;
    }

    public List<String> getMaskKeywords() {
        return maskKeywords;
    }

    public void setMaskKeywords(List<String> maskKeywords) {
        this.maskKeywords = maskKeywords;
    }
}

