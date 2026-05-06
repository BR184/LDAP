package com.company.idm.domain.mail;

/**
 * 测试邮件消息体。
 */
public record MailTestMessage(
    String toAddress,
    String subject,
    String content
) {
}
