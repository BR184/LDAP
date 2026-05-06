package com.company.idm.application.mail;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.MailSecurityProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 邮件密码加解密服务。
 */
public class MailConfigCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final MailSecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public MailConfigCryptoService(MailSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, buildSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv)
                .put(encrypted)
                .array());
        } catch (GeneralSecurityException exception) {
            throw new BizException("MAIL_CONFIG_ENCRYPT_FAILED", "邮件配置密码加密失败");
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            ByteBuffer byteBuffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encrypted = new byte[byteBuffer.remaining()];
            byteBuffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, buildSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BizException("MAIL_CONFIG_DECRYPT_FAILED", "邮件配置密码解密失败");
        }
    }

    private SecretKeySpec buildSecretKey() {
        String secretKey = securityProperties.getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new BizException("MAIL_CONFIG_SECRET_KEY_MISSING", "邮件配置主密钥未配置");
        }
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new BizException("MAIL_CONFIG_SECRET_KEY_INVALID", "邮件配置主密钥长度必须为 16、24 或 32 字节");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
