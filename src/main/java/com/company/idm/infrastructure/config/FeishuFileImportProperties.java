package com.company.idm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定飞书标准化文件导入相关配置项。
 */
@ConfigurationProperties(prefix = "app.sync.feishu.file-import")
public class FeishuFileImportProperties {

    private boolean enabled = true;
    private String rootDir = "docs/feishu-import";
    private long maxFileSizeBytes = 2 * 1024 * 1024;
    private int maxRecoveryFiles = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRootDir() {
        return rootDir;
    }

    public void setRootDir(String rootDir) {
        this.rootDir = rootDir;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getMaxRecoveryFiles() {
        return maxRecoveryFiles;
    }

    public void setMaxRecoveryFiles(int maxRecoveryFiles) {
        this.maxRecoveryFiles = maxRecoveryFiles;
    }
}
