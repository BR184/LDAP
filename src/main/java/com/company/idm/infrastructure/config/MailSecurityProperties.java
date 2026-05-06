package com.company.idm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定邮件配置密钥相关参数。
 */
@ConfigurationProperties(prefix = "app.mail.security")
public class MailSecurityProperties {

    private String secretKey = "";

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
