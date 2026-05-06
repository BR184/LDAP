package com.company.idm.application.mail;

/**
 * 测试邮件返回结果。
 */
public record MailConfigTestResult(
    boolean success,
    String message
) {
}
