package com.company.idm.application.mail;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.mail.MailSender;
import com.company.idm.domain.mail.MailServerConfig;
import com.company.idm.domain.mail.MailServerConfigRepository;
import com.company.idm.domain.mail.MailTestMessage;
import com.company.idm.infrastructure.config.MailSecurityProperties;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邮件配置应用服务。
 */
@Service
public class MailConfigApplicationService {

    private static final List<String> SUPPORTED_SEND_MODES = List.of("SMTP");
    private static final List<String> SUPPORTED_SECURE_MODES = List.of("NONE", "STARTTLS", "SSL_TLS");

    private final MailServerConfigRepository mailServerConfigRepository;
    private final AuditLogRepository auditLogRepository;
    private final MailSender mailSender;
    private final MailConfigCryptoService cryptoService;

    public MailConfigApplicationService(
        MailServerConfigRepository mailServerConfigRepository,
        AuditLogRepository auditLogRepository,
        MailSender mailSender,
        MailSecurityProperties mailSecurityProperties
    ) {
        this.mailServerConfigRepository = mailServerConfigRepository;
        this.auditLogRepository = auditLogRepository;
        this.mailSender = mailSender;
        this.cryptoService = new MailConfigCryptoService(mailSecurityProperties);
    }

    public MailServerConfigDetail getCurrentConfig() {
        return mailServerConfigRepository.findCurrent()
            .map(this::toDetail)
            .orElse(null);
    }

    @Transactional
    public MailServerConfigDetail saveConfig(SaveMailConfigCommand command) {
        validateConfig(command.sendMode(), command.secureMode(), command.host(), command.port(), command.fromAddress(),
            command.authRequired(), command.username(), command.password(),
            mailServerConfigRepository.findCurrent().map(MailServerConfig::getPasswordCiphertext).orElse(null));

        MailServerConfig existing = mailServerConfigRepository.findCurrent().orElse(null);
        String passwordCiphertext = resolvePasswordCiphertext(existing, command.password(), command.authRequired());
        MailServerConfig saved = mailServerConfigRepository.save(MailServerConfig.builder()
            .id(existing == null ? null : existing.getId())
            .sendMode(normalizeUpper(command.sendMode()))
            .secureMode(normalizeUpper(command.secureMode()))
            .host(normalize(command.host()))
            .port(command.port())
            .fromAddress(normalize(command.fromAddress()))
            .fromName(normalize(command.fromName()))
            .authRequired(Boolean.TRUE.equals(command.authRequired()))
            .username(normalize(command.username()))
            .passwordCiphertext(passwordCiphertext)
            .enabled(command.enabled() == null || command.enabled())
            .remark(normalize(command.remark()))
            .lastTestSuccess(existing == null ? null : existing.getLastTestSuccess())
            .lastTestAt(existing == null ? null : existing.getLastTestAt())
            .lastTestMessage(existing == null ? null : existing.getLastTestMessage())
            .build());
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("MAIL_CONFIG_SAVE")
            .bizType("SYSTEM")
            .bizId(saved.getId() == null ? "MAIL_CONFIG" : String.valueOf(saved.getId()))
            .afterJson(saved.getHost())
            .result("SUCCESS")
            .build());
        return toDetail(saved);
    }

    public MailConfigTestResult testConfig(MailConfigTestCommand command) {
        MailServerConfig existing = mailServerConfigRepository.findCurrent().orElse(null);
        validateConfig(command.sendMode(), command.secureMode(), command.host(), command.port(), command.fromAddress(),
            command.authRequired(), command.username(), command.password(),
            existing == null ? null : existing.getPasswordCiphertext());
        requireEmail(command.testToAddress(), "测试收件人邮箱格式错误");
        MailServerConfig config = MailServerConfig.builder()
            .sendMode(normalizeUpper(command.sendMode()))
            .secureMode(normalizeUpper(command.secureMode()))
            .host(normalize(command.host()))
            .port(command.port())
            .fromAddress(normalize(command.fromAddress()))
            .fromName(normalize(command.fromName()))
            .authRequired(Boolean.TRUE.equals(command.authRequired()))
            .username(normalize(command.username()))
            .enabled(true)
            .build();
        String rawPassword = resolveRawPassword(command.password(), existing, command.authRequired());
        mailSender.sendTestMail(config, rawPassword, new MailTestMessage(
            normalize(command.testToAddress()),
            "统一身份管理平台邮件配置测试",
            "这是一封系统自动发送的测试邮件，若您收到该邮件，说明当前 SMTP 配置可用。"
        ));
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("MAIL_CONFIG_TEST")
            .bizType("SYSTEM")
            .bizId("MAIL_CONFIG_TEST")
            .afterJson(config.getHost())
            .result("SUCCESS")
            .build());
        return new MailConfigTestResult(true, "测试邮件发送成功");
    }

    public String decryptPassword(String ciphertext) {
        return cryptoService.decrypt(ciphertext);
    }

    private MailServerConfigDetail toDetail(MailServerConfig config) {
        return new MailServerConfigDetail(
            config.getId(),
            config.getSendMode(),
            config.getSecureMode(),
            config.getHost(),
            config.getPort(),
            config.getFromAddress(),
            config.getFromName(),
            config.getAuthRequired(),
            config.getUsername(),
            config.getPasswordCiphertext() != null && !config.getPasswordCiphertext().isBlank(),
            config.getEnabled(),
            config.getRemark(),
            config.getLastTestSuccess(),
            config.getLastTestAt(),
            config.getLastTestMessage()
        );
    }

    private String resolvePasswordCiphertext(MailServerConfig existing, String rawPassword, Boolean authRequired) {
        if (!Boolean.TRUE.equals(authRequired)) {
            return null;
        }
        String normalizedPassword = normalize(rawPassword);
        if (normalizedPassword != null) {
            return cryptoService.encrypt(normalizedPassword);
        }
        if (existing != null && existing.getPasswordCiphertext() != null && !existing.getPasswordCiphertext().isBlank()) {
            return existing.getPasswordCiphertext();
        }
        throw new BizException("MAIL_CONFIG_PASSWORD_REQUIRED", "启用认证时必须填写邮箱密码或授权码");
    }

    private String resolveRawPassword(String rawPassword, MailServerConfig existing, Boolean authRequired) {
        if (!Boolean.TRUE.equals(authRequired)) {
            return null;
        }
        String normalizedPassword = normalize(rawPassword);
        if (normalizedPassword != null) {
            return normalizedPassword;
        }
        if (existing != null && existing.getPasswordCiphertext() != null && !existing.getPasswordCiphertext().isBlank()) {
            return cryptoService.decrypt(existing.getPasswordCiphertext());
        }
        throw new BizException("MAIL_CONFIG_PASSWORD_REQUIRED", "启用认证时必须填写邮箱密码或授权码");
    }

    private void validateConfig(
        String sendMode,
        String secureMode,
        String host,
        Integer port,
        String fromAddress,
        Boolean authRequired,
        String username,
        String password,
        String existingPasswordCiphertext
    ) {
        String normalizedSendMode = normalizeUpper(sendMode);
        if (!SUPPORTED_SEND_MODES.contains(normalizedSendMode)) {
            throw new BizException("MAIL_CONFIG_SEND_MODE_INVALID", "当前仅支持 SMTP 发送模式");
        }
        String normalizedSecureMode = normalizeUpper(secureMode);
        if (!SUPPORTED_SECURE_MODES.contains(normalizedSecureMode)) {
            throw new BizException("MAIL_CONFIG_SECURE_MODE_INVALID", "邮件加密方式不支持");
        }
        if (normalize(host) == null) {
            throw new BizException("MAIL_CONFIG_HOST_REQUIRED", "SMTP 服务器地址不能为空");
        }
        if (port == null || port <= 0 || port > 65535) {
            throw new BizException("MAIL_CONFIG_PORT_INVALID", "SMTP 端口范围必须在 1 到 65535 之间");
        }
        requireEmail(fromAddress, "发件邮箱格式错误");
        if (Boolean.TRUE.equals(authRequired) && normalize(username) == null) {
            throw new BizException("MAIL_CONFIG_USERNAME_REQUIRED", "启用认证时必须填写邮箱账号");
        }
        if (Boolean.TRUE.equals(authRequired)
            && normalize(password) == null
            && (existingPasswordCiphertext == null || existingPasswordCiphertext.isBlank())) {
            throw new BizException("MAIL_CONFIG_PASSWORD_REQUIRED", "启用认证时必须填写邮箱密码或授权码");
        }
    }

    private void requireEmail(String email, String message) {
        String normalizedEmail = normalize(email);
        if (normalizedEmail == null) {
            throw new BizException("MAIL_CONFIG_EMAIL_REQUIRED", message);
        }
        try {
            InternetAddress internetAddress = new InternetAddress(normalizedEmail);
            internetAddress.validate();
        } catch (AddressException exception) {
            throw new BizException("MAIL_CONFIG_EMAIL_INVALID", message);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeUpper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase();
    }
}
