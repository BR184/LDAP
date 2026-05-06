package com.company.idm.interfaces.system;

import java.time.LocalDateTime;

/**
 * 邮件配置响应。
 */
public record MailConfigResponse(
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
