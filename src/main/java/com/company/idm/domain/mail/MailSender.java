package com.company.idm.domain.mail;

/**
 * 邮件发送领域能力。
 */
public interface MailSender {

    void sendPlainTextMail(MailServerConfig config, String rawPassword, String toAddress, String subject, String text);

    void sendTestMail(MailServerConfig config, String rawPassword, MailTestMessage message);
}
