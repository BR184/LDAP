package com.company.idm.application.token;

public record EncryptedPersonalAccessTokenSecret(
    String ciphertext,
    String keyId
) {
}
