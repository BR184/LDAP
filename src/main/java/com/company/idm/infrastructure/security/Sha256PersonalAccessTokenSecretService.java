package com.company.idm.infrastructure.security;

import com.company.idm.application.token.GeneratedPersonalAccessTokenSecret;
import com.company.idm.application.token.PersonalAccessTokenSecretService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class Sha256PersonalAccessTokenSecretService implements PersonalAccessTokenSecretService {

    static final String TOKEN_PREFIX = "idm_pat_";
    static final int TOKEN_UID_LENGTH = 22;
    static final int SECRET_LENGTH = 43;
    static final int HASH_VERSION = 1;

    private static final Pattern BASE64_URL = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private final SecureRandom secureRandom;

    public Sha256PersonalAccessTokenSecretService() {
        this(new SecureRandom());
    }

    Sha256PersonalAccessTokenSecretService(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public GeneratedPersonalAccessTokenSecret generate() {
        String tokenUid = randomBase64Url(16);
        String secret = randomBase64Url(32);
        String rawToken = TOKEN_PREFIX + tokenUid + "_" + secret;
        return new GeneratedPersonalAccessTokenSecret(
            tokenUid,
            rawToken,
            hash(rawToken),
            HASH_VERSION,
            TOKEN_PREFIX + tokenUid + "_..."
        );
    }

    @Override
    public Optional<String> extractTokenUid(String rawToken) {
        if (!hasValidFormat(rawToken)) {
            return Optional.empty();
        }
        return Optional.of(rawToken.substring(TOKEN_PREFIX.length(), TOKEN_PREFIX.length() + TOKEN_UID_LENGTH));
    }

    @Override
    public boolean verify(String rawToken, String expectedHash, int hashVersion) {
        if (hashVersion != HASH_VERSION || !hasValidFormat(rawToken) || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        try {
            byte[] actual = BASE64_DECODER.decode(hash(rawToken));
            byte[] expected = BASE64_DECODER.decode(expectedHash);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasValidFormat(String rawToken) {
        if (rawToken == null) {
            return false;
        }
        int separatorIndex = TOKEN_PREFIX.length() + TOKEN_UID_LENGTH;
        if (rawToken.length() != separatorIndex + 1 + SECRET_LENGTH
            || !rawToken.startsWith(TOKEN_PREFIX)
            || rawToken.charAt(separatorIndex) != '_') {
            return false;
        }
        String tokenUid = rawToken.substring(TOKEN_PREFIX.length(), separatorIndex);
        String secret = rawToken.substring(separatorIndex + 1);
        return BASE64_URL.matcher(tokenUid).matches() && BASE64_URL.matcher(secret).matches();
    }

    private String randomBase64Url(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return BASE64_ENCODER.encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return BASE64_ENCODER.encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
