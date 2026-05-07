package com.company.idm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定密码重置邮件与限流相关配置。
 */
@ConfigurationProperties(prefix = "app.password-reset")
public class PasswordResetProperties {

    private String mailSubject = "统一身份管理平台密码重置通知";
    private String mailFrom = "";
    private String forgotPasswordSuccessNotice = "如账号信息有效，系统已向绑定内网邮箱发送重置邮件，请注意查收";
    private int usernameCooldownSeconds = 300;
    private int ipWindowSeconds = 300;
    private int ipMaxAttempts = 10;

    public String getMailSubject() {
        return mailSubject;
    }

    public void setMailSubject(String mailSubject) {
        this.mailSubject = mailSubject;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public String getForgotPasswordSuccessNotice() {
        return forgotPasswordSuccessNotice;
    }

    public void setForgotPasswordSuccessNotice(String forgotPasswordSuccessNotice) {
        this.forgotPasswordSuccessNotice = forgotPasswordSuccessNotice;
    }

    public int getUsernameCooldownSeconds() {
        return usernameCooldownSeconds;
    }

    public void setUsernameCooldownSeconds(int usernameCooldownSeconds) {
        this.usernameCooldownSeconds = usernameCooldownSeconds;
    }

    public int getIpWindowSeconds() {
        return ipWindowSeconds;
    }

    public void setIpWindowSeconds(int ipWindowSeconds) {
        this.ipWindowSeconds = ipWindowSeconds;
    }

    public int getIpMaxAttempts() {
        return ipMaxAttempts;
    }

    public void setIpMaxAttempts(int ipMaxAttempts) {
        this.ipMaxAttempts = ipMaxAttempts;
    }
}
