package com.company.idm.infrastructure.mail;

import com.company.idm.domain.user.PasswordResetNotificationService;
import com.company.idm.domain.mail.MailSender;
import com.company.idm.domain.mail.MailServerConfigRepository;
import com.company.idm.application.mail.MailConfigApplicationService;
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
        PasswordResetProperties passwordResetProperties,
        MailServerConfigRepository mailServerConfigRepository,
        MailConfigApplicationService mailConfigApplicationService,
        MailSender mailSender
    ) {
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        PasswordResetNotificationService fallbackNotificationService = javaMailSender != null
            ? new SmtpPasswordResetNotificationService(javaMailSender, passwordResetProperties)
            : new NoopPasswordResetNotificationService();
        return new DynamicPasswordResetNotificationService(
            mailServerConfigRepository,
            mailConfigApplicationService,
            mailSender,
            passwordResetProperties,
            fallbackNotificationService
        );
    }
}
