package com.company.idm.test;

import com.company.idm.application.mail.MailConfigApplicationService;
import com.company.idm.domain.mail.MailSender;
import com.company.idm.domain.mail.MailServerConfigRepository;
import com.company.idm.domain.user.PasswordResetNotificationService;
import com.company.idm.infrastructure.config.MailSecurityProperties;
import com.company.idm.infrastructure.mail.DynamicPasswordResetNotificationService;
import com.company.idm.infrastructure.config.PasswordResetProperties;
import com.company.idm.infrastructure.mail.PasswordResetNotificationConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PasswordResetNotificationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void shouldProvideNoopNotificationServiceWhenJavaMailSenderMissing() {
        contextRunner
            .withUserConfiguration(
                PasswordResetNotificationConfiguration.class,
                PasswordResetPropertiesTestConfiguration.class,
                DynamicMailTestConfiguration.class
            )
            .run(context -> {
                assertThat(context).hasSingleBean(PasswordResetNotificationService.class);
                assertThat(context.getBean(PasswordResetNotificationService.class))
                    .isInstanceOf(DynamicPasswordResetNotificationService.class);
            });
    }

    @Test
    void shouldProvideSmtpNotificationServiceWhenJavaMailSenderPresent() {
        contextRunner
            .withUserConfiguration(
                PasswordResetNotificationConfiguration.class,
                PasswordResetPropertiesTestConfiguration.class,
                DynamicMailTestConfiguration.class,
                JavaMailSenderTestConfiguration.class
            )
            .run(context -> {
                assertThat(context).hasSingleBean(PasswordResetNotificationService.class);
                assertThat(context.getBean(PasswordResetNotificationService.class))
                    .isInstanceOf(DynamicPasswordResetNotificationService.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class PasswordResetPropertiesTestConfiguration {

        @Bean
        PasswordResetProperties passwordResetProperties() {
            return new PasswordResetProperties();
        }

        @Bean
        MailSecurityProperties mailSecurityProperties() {
            MailSecurityProperties properties = new MailSecurityProperties();
            properties.setSecretKey("12345678901234567890123456789012");
            return properties;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DynamicMailTestConfiguration {

        @Bean
        MailServerConfigRepository mailServerConfigRepository() {
            return mock(MailServerConfigRepository.class);
        }

        @Bean
        MailSender mailSender() {
            return mock(MailSender.class);
        }

        @Bean
        MailConfigApplicationService mailConfigApplicationService(
            MailServerConfigRepository mailServerConfigRepository,
            MailSender mailSender,
            MailSecurityProperties mailSecurityProperties,
            PasswordResetProperties passwordResetProperties
        ) {
            return new MailConfigApplicationService(
                mailServerConfigRepository,
                mock(com.company.idm.domain.audit.AuditLogRepository.class),
                mailSender,
                mailSecurityProperties
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JavaMailSenderTestConfiguration {

        @Bean
        JavaMailSender javaMailSender() {
            return mock(JavaMailSender.class);
        }
    }
}
