package com.company.idm.test;

import com.company.idm.application.mail.MailConfigApplicationService;
import com.company.idm.application.mail.MailConfigTestCommand;
import com.company.idm.application.mail.SaveMailConfigCommand;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.mail.MailServerConfig;
import com.company.idm.domain.mail.MailServerConfigRepository;
import com.company.idm.domain.mail.MailSender;
import com.company.idm.domain.mail.MailTestMessage;
import com.company.idm.infrastructure.config.MailSecurityProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailConfigApplicationServiceTest {

    @Mock
    private MailServerConfigRepository mailServerConfigRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private MailSender mailSender;

    @Test
    void shouldEncryptPasswordWhenSavingMailConfig() {
        MailConfigApplicationService service = buildService();
        when(mailServerConfigRepository.findCurrent()).thenReturn(Optional.empty());
        when(mailServerConfigRepository.save(any(MailServerConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveConfig(new SaveMailConfigCommand(
            "SMTP",
            "STARTTLS",
            "10.0.100.49",
            587,
            "huayun@crowncad.com",
            "CrownCAD",
            true,
            "huayun@crowncad.com",
            "mail-password",
            true,
            "内网邮件配置",
            "admin"
        ));

        ArgumentCaptor<MailServerConfig> configCaptor = ArgumentCaptor.forClass(MailServerConfig.class);
        verify(mailServerConfigRepository).save(configCaptor.capture());
        assertThat(configCaptor.getValue().getPasswordCiphertext()).isNotBlank();
        assertThat(configCaptor.getValue().getPasswordCiphertext()).isNotEqualTo("mail-password");
    }

    @Test
    void shouldKeepExistingPasswordWhenSavingWithoutNewPassword() {
        MailConfigApplicationService service = buildService();
        MailServerConfig existing = MailServerConfig.builder()
            .id(1L)
            .sendMode("SMTP")
            .secureMode("STARTTLS")
            .host("10.0.100.49")
            .port(587)
            .fromAddress("huayun@crowncad.com")
            .fromName("CrownCAD")
            .authRequired(true)
            .username("huayun@crowncad.com")
            .passwordCiphertext("cipher-text")
            .enabled(true)
            .remark("old")
            .build();
        when(mailServerConfigRepository.findCurrent()).thenReturn(Optional.of(existing));
        when(mailServerConfigRepository.save(any(MailServerConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveConfig(new SaveMailConfigCommand(
            "SMTP",
            "STARTTLS",
            "10.0.100.50",
            587,
            "huayun@crowncad.com",
            "CrownCAD",
            true,
            "huayun@crowncad.com",
            null,
            true,
            "new",
            "admin"
        ));

        ArgumentCaptor<MailServerConfig> configCaptor = ArgumentCaptor.forClass(MailServerConfig.class);
        verify(mailServerConfigRepository).save(configCaptor.capture());
        assertThat(configCaptor.getValue().getPasswordCiphertext()).isEqualTo("cipher-text");
    }

    @Test
    void shouldSendTestMailWithResolvedConfig() {
        MailConfigApplicationService service = buildService();

        service.testConfig(new MailConfigTestCommand(
            "SMTP",
            "STARTTLS",
            "10.0.100.49",
            587,
            "huayun@crowncad.com",
            "CrownCAD",
            true,
            "huayun@crowncad.com",
            "mail-password",
            "receiver@crowncad.com",
            "admin"
        ));

        ArgumentCaptor<MailTestMessage> messageCaptor = ArgumentCaptor.forClass(MailTestMessage.class);
        verify(mailSender).sendTestMail(any(MailServerConfig.class), org.mockito.ArgumentMatchers.eq("mail-password"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().toAddress()).isEqualTo("receiver@crowncad.com");
    }

    private MailConfigApplicationService buildService() {
        MailSecurityProperties securityProperties = new MailSecurityProperties();
        securityProperties.setSecretKey("12345678901234567890123456789012");
        return new MailConfigApplicationService(
            mailServerConfigRepository,
            auditLogRepository,
            mailSender,
            securityProperties
        );
    }
}
