package com.company.idm.infrastructure.mail;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.mail.MailSender;
import com.company.idm.domain.mail.MailServerConfig;
import com.company.idm.domain.mail.MailTestMessage;
import java.util.Properties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * 基于动态 SMTP 配置发送邮件。
 */
@Service
public class DynamicMailSender implements MailSender {

    @Override
    public void sendPlainTextMail(MailServerConfig config, String rawPassword, String toAddress, String subject, String text) {
        JavaMailSenderImpl sender = buildSender(config, rawPassword);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toAddress);
        message.setSubject(subject);
        message.setText(text);
        if (config.getFromAddress() != null && !config.getFromAddress().isBlank()) {
            message.setFrom(config.getFromAddress());
        }
        sender.send(message);
    }

    @Override
    public void sendTestMail(MailServerConfig config, String rawPassword, MailTestMessage message) {
        sendPlainTextMail(config, rawPassword, message.toAddress(), message.subject(), message.content());
    }

    private JavaMailSenderImpl buildSender(MailServerConfig config, String rawPassword) {
        if (config == null) {
            throw new BizException("MAIL_CONFIG_NOT_FOUND", "邮件配置不存在");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort() == null ? 25 : config.getPort());
        sender.setProtocol("smtp");
        if (Boolean.TRUE.equals(config.getAuthRequired())) {
            sender.setUsername(config.getUsername());
            sender.setPassword(rawPassword);
        }
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", String.valueOf(Boolean.TRUE.equals(config.getAuthRequired())));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        String secureMode = config.getSecureMode() == null ? "NONE" : config.getSecureMode().trim().toUpperCase();
        if ("STARTTLS".equals(secureMode)) {
            properties.put("mail.smtp.starttls.enable", "true");
        }
        if ("SSL_TLS".equals(secureMode)) {
            properties.put("mail.smtp.ssl.enable", "true");
        }
        return sender;
    }
}
