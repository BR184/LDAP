package com.company.idm.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.idm.application.token.EncryptedPersonalAccessTokenSecret;
import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.PersonalAccessTokenEncryptionProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AesGcmPersonalAccessTokenEncryptionServiceTest {

    @Test
    void encryptsWithRandomIvAndDecryptsWithMatchingOwnerAndTokenUid() {
        AesGcmPersonalAccessTokenEncryptionService service = service();

        EncryptedPersonalAccessTokenSecret first = service.encrypt("idm_pat_uid_secret", 7L, "uid");
        EncryptedPersonalAccessTokenSecret second = service.encrypt("idm_pat_uid_secret", 7L, "uid");

        assertThat(first.keyId()).isEqualTo("key-2026-08");
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(service.decrypt(first.ciphertext(), first.keyId(), 7L, "uid"))
            .isEqualTo("idm_pat_uid_secret");
    }

    @Test
    void rejectsCiphertextMovedToAnotherOwner() {
        AesGcmPersonalAccessTokenEncryptionService service = service();
        EncryptedPersonalAccessTokenSecret encrypted = service.encrypt("idm_pat_uid_secret", 7L, "uid");

        assertThatThrownBy(() -> service.decrypt(encrypted.ciphertext(), encrypted.keyId(), 8L, "uid"))
            .isInstanceOf(BizException.class)
            .hasMessage("个人访问密钥解密失败");
    }

    @Test
    void rejectsEncryptionWhenTheActiveKeyIsMissing() {
        PersonalAccessTokenEncryptionProperties properties = new PersonalAccessTokenEncryptionProperties();
        AesGcmPersonalAccessTokenEncryptionService service =
            new AesGcmPersonalAccessTokenEncryptionService(properties);

        assertThatThrownBy(() -> service.encrypt("idm_pat_uid_secret", 7L, "uid"))
            .isInstanceOf(BizException.class)
            .hasMessage("个人访问密钥加密主密钥未配置");
    }

    private AesGcmPersonalAccessTokenEncryptionService service() {
        PersonalAccessTokenEncryptionProperties properties = new PersonalAccessTokenEncryptionProperties();
        properties.setActiveKeyId("key-2026-08");
        properties.setKeys(Map.of(
            "key-2026-08",
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef"
                .getBytes(StandardCharsets.UTF_8))
        ));
        return new AesGcmPersonalAccessTokenEncryptionService(properties);
    }
}
