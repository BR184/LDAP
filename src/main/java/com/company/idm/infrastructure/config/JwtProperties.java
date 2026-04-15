package com.company.idm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 JWT 签名与过期时间配置。
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "prototype-secret-prototype-secret-prototype-secret";
    private long expireMinutes = 30;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }

    public void setExpireMinutes(long expireMinutes) {
        this.expireMinutes = expireMinutes;
    }
}
