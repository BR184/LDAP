package com.company.idm.application.mail;

/**
 * 保存邮件配置命令。
 */
public record SaveMailConfigCommand(
    String sendMode,
    String secureMode,
    String host,
    Integer port,
    String fromAddress,
    String fromName,
    Boolean authRequired,
    String username,
    String password,
    Boolean enabled,
    String remark,
    String operator
) {
}
