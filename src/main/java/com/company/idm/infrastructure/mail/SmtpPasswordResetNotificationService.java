package com.company.idm.infrastructure.mail;

import com.company.idm.domain.user.PasswordResetNotificationService;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.config.PasswordResetProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 基于 SMTP 发送密码重置邮件。
 */
@RequiredArgsConstructor
public class SmtpPasswordResetNotificationService implements PasswordResetNotificationService {

    private final JavaMailSender javaMailSender;
    private final PasswordResetProperties passwordResetProperties;

    @Override
    public void sendPasswordResetMail(User user, String rawPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (passwordResetProperties.getMailFrom() != null && !passwordResetProperties.getMailFrom().isBlank()) {
            message.setFrom(passwordResetProperties.getMailFrom());
        }
        message.setTo(user.getEmail());
        message.setSubject(passwordResetProperties.getMailSubject());
        message.setText("""
            当前密码已重置为：%s，请尽快修改密码！
            如非本人操作，请立即联系管理员。
            """.formatted(rawPassword));
        javaMailSender.send(message);
    }
}
