package com.company.idm.interfaces.system;

/**
 * 测试邮件响应。
 */
public record MailConfigTestResponse(
    boolean success,
    String message
) {
}
