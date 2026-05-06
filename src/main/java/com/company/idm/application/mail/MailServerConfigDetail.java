package com.company.idm.application.mail;

import java.time.LocalDateTime;

/**
 * 邮件配置详情视图。
 */
public record MailServerConfigDetail(
    Long id,
    String sendMode,
    String secureMode,
    String host,
    Integer port,
    String fromAddress,
    String fromName,
    Boolean authRequired,
    String username,
    boolean passwordConfigured,
    Boolean enabled,
    String remark,
    Boolean lastTestSuccess,
    LocalDateTime lastTestAt,
    String lastTestMessage
) {
}
