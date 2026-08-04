package com.company.idm.application.token;

public interface PersonalAccessTokenEncryptionService {

    EncryptedPersonalAccessTokenSecret encrypt(String rawToken, Long ownerId, String tokenUid);

    String decrypt(String ciphertext, String keyId, Long ownerId, String tokenUid);
}
