package com.company.idm.infrastructure.security;

import com.company.idm.application.token.EncryptedPersonalAccessTokenSecret;
import com.company.idm.application.token.PersonalAccessTokenEncryptionService;
import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.PersonalAccessTokenEncryptionProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AesGcmPersonalAccessTokenEncryptionService implements PersonalAccessTokenEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int KEY_LENGTH_BYTES = 32;

    private final PersonalAccessTokenEncryptionProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public AesGcmPersonalAccessTokenEncryptionService(PersonalAccessTokenEncryptionProperties properties) {
        this(properties, new SecureRandom());
    }

    AesGcmPersonalAccessTokenEncryptionService(
        PersonalAccessTokenEncryptionProperties properties,
        SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    @Override
    public EncryptedPersonalAccessTokenSecret encrypt(String rawToken, Long ownerId, String tokenUid) {
        String activeKeyId = properties.getActiveKeyId();
        if (activeKeyId.isBlank() || !properties.getKeys().containsKey(activeKeyId)) {
            throw new BizException("PAT_ENCRYPTION_KEY_MISSING", "个人访问密钥加密主密钥未配置");
        }
        validateContext(rawToken, ownerId, tokenUid);
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey(activeKeyId),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );
            cipher.updateAAD(aad(ownerId, tokenUid));
            byte[] encrypted = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv)
                .put(encrypted)
                .array();
            return new EncryptedPersonalAccessTokenSecret(
                Base64.getEncoder().encodeToString(payload),
                activeKeyId
            );
        } catch (GeneralSecurityException exception) {
            throw new BizException("PAT_ENCRYPTION_FAILED", "个人访问密钥加密失败");
        }
    }

    @Override
    public String decrypt(String ciphertext, String keyId, Long ownerId, String tokenUid) {
        if (ciphertext == null || ciphertext.isBlank() || keyId == null || keyId.isBlank()) {
            throw new BizException("PAT_SECRET_UNRECOVERABLE", "当前个人访问密钥不可恢复");
        }
        validateContext(ciphertext, ownerId, tokenUid);
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new GeneralSecurityException("Invalid encrypted payload");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(keyId),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );
            cipher.updateAAD(aad(ownerId, tokenUid));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BizException("PAT_DECRYPTION_FAILED", "个人访问密钥解密失败");
        }
    }

    private SecretKeySpec secretKey(String keyId) {
        String encodedKey = properties.getKeys().get(keyId);
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new BizException("PAT_ENCRYPTION_KEY_UNAVAILABLE", "个人访问密钥加密主密钥不可用");
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != KEY_LENGTH_BYTES) {
                throw new IllegalArgumentException("Invalid AES-256 key length");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new BizException("PAT_ENCRYPTION_KEY_INVALID", "个人访问密钥加密主密钥格式无效");
        }
    }

    private byte[] aad(Long ownerId, String tokenUid) {
        return (ownerId + ":" + tokenUid).getBytes(StandardCharsets.UTF_8);
    }

    private void validateContext(String value, Long ownerId, String tokenUid) {
        if (value == null || value.isBlank() || ownerId == null || tokenUid == null || tokenUid.isBlank()) {
            throw new BizException("PAT_ENCRYPTION_CONTEXT_INVALID", "个人访问密钥加密上下文无效");
        }
    }
}
