package com.company.idm.test;

import com.company.idm.domain.user.PasswordResetNotificationService;
import com.company.idm.infrastructure.config.PasswordResetProperties;
import com.company.idm.infrastructure.mail.NoopPasswordResetNotificationService;
import com.company.idm.infrastructure.mail.PasswordResetNotificationConfiguration;
import com.company.idm.infrastructure.mail.SmtpPasswordResetNotificationService;
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
                PasswordResetPropertiesTestConfiguration.class
            )
            .run(context -> {
                assertThat(context).hasSingleBean(PasswordResetNotificationService.class);
                assertThat(context.getBean(PasswordResetNotificationService.class))
                    .isInstanceOf(NoopPasswordResetNotificationService.class);
            });
    }

    @Test
    void shouldProvideSmtpNotificationServiceWhenJavaMailSenderPresent() {
        contextRunner
            .withUserConfiguration(
                PasswordResetNotificationConfiguration.class,
                PasswordResetPropertiesTestConfiguration.class,
                JavaMailSenderTestConfiguration.class
            )
            .run(context -> {
                assertThat(context).hasSingleBean(PasswordResetNotificationService.class);
                assertThat(context.getBean(PasswordResetNotificationService.class))
                    .isInstanceOf(SmtpPasswordResetNotificationService.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class PasswordResetPropertiesTestConfiguration {

        @Bean
        PasswordResetProperties passwordResetProperties() {
            return new PasswordResetProperties();
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
