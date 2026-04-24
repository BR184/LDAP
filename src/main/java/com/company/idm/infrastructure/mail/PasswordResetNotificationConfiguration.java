package com.company.idm.infrastructure.mail;

import com.company.idm.domain.user.PasswordResetNotificationService;
import com.company.idm.infrastructure.config.PasswordResetProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 负责按运行环境装配密码重置通知实现。
 */
@Configuration(proxyBeanMethods = false)
public class PasswordResetNotificationConfiguration {

    @Bean
    public PasswordResetNotificationService passwordResetNotificationService(
        ObjectProvider<JavaMailSender> javaMailSenderProvider,
        PasswordResetProperties passwordResetProperties
    ) {
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender != null) {
            return new SmtpPasswordResetNotificationService(javaMailSender, passwordResetProperties);
        }
        return new NoopPasswordResetNotificationService();
    }
}
