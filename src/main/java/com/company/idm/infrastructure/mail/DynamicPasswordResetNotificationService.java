package com.company.idm.infrastructure.mail;

import com.company.idm.application.mail.MailConfigApplicationService;
import com.company.idm.domain.mail.MailSender;
import com.company.idm.domain.mail.MailServerConfig;
import com.company.idm.domain.mail.MailServerConfigRepository;
import com.company.idm.domain.user.PasswordResetNotificationService;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.config.PasswordResetProperties;
import java.util.Optional;

/**
 * 支持数据库动态配置和旧 SMTP Bean 双模式的密码重置邮件服务。
 */
public class DynamicPasswordResetNotificationService implements PasswordResetNotificationService {

    private final MailServerConfigRepository mailServerConfigRepository;
    private final MailConfigApplicationService mailConfigApplicationService;
    private final MailSender mailSender;
    private final PasswordResetProperties passwordResetProperties;
    private final PasswordResetNotificationService fallbackNotificationService;

    public DynamicPasswordResetNotificationService(
        MailServerConfigRepository mailServerConfigRepository,
        MailConfigApplicationService mailConfigApplicationService,
        MailSender mailSender,
        PasswordResetProperties passwordResetProperties,
        PasswordResetNotificationService fallbackNotificationService
    ) {
        this.mailServerConfigRepository = mailServerConfigRepository;
        this.mailConfigApplicationService = mailConfigApplicationService;
        this.mailSender = mailSender;
        this.passwordResetProperties = passwordResetProperties;
        this.fallbackNotificationService = fallbackNotificationService;
    }

    @Override
    public void sendPasswordResetMail(User user, String rawPassword) {
        Optional<MailServerConfig> enabledConfig = mailServerConfigRepository.findEnabled();
        if (enabledConfig.isEmpty()) {
            fallbackNotificationService.sendPasswordResetMail(user, rawPassword);
            return;
        }
        MailServerConfig config = enabledConfig.get();
        String decryptedPassword = mailConfigApplicationService.decryptPassword(config.getPasswordCiphertext());
        mailSender.sendPlainTextMail(
            config,
            decryptedPassword,
            user.getEmail(),
            passwordResetProperties.getMailSubject(),
            """
                当前密码已重置为：%s，请尽快修改密码！
                如非本人操作，请立即联系管理员。
                """.formatted(rawPassword)
        );
    }
}
