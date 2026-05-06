package com.company.idm.application.mail;

/**
 * 测试邮件配置命令。
 */
public record MailConfigTestCommand(
    String sendMode,
    String secureMode,
    String host,
    Integer port,
    String fromAddress,
    String fromName,
    Boolean authRequired,
    String username,
    String password,
    String testToAddress,
    String operator
) {
}
