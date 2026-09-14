package com.tradeguide.service.broker;

import com.tradeguide.config.BrokerCredentialKeyringProperties;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AesGcmBrokerCredentialCipher implements BrokerCredentialCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int INITIALIZATION_VECTOR_LENGTH_BYTES = 12;
    private static final int AUTHENTICATION_TAG_LENGTH_BITS = 128;
    private static final int LEGACY_KEY_VERSION = 1;

    private final Map<Integer, SecretKey> secretKeysByVersion;
    private final Integer currentVersion;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmBrokerCredentialCipher(BrokerCredentialKeyringProperties keyringProperties) {
        Map<Integer, SecretKey> keysByVersion = new LinkedHashMap<>();
        for (BrokerCredentialKeyringProperties.KeyEntry entry : keyringProperties.encryptionKeys()) {
            if (entry.value().isBlank()) {
                continue;
            }
            if (keysByVersion.containsKey(entry.version())) {
                throw new IllegalStateException(
                        "tradeguide.broker.encryption-keys must not contain duplicate version "
                                + entry.version() + "."
                );
            }
            keysByVersion.put(entry.version(), decodeKey(
                    entry.value(),
                    "BROKER_CREDENTIAL_ENCRYPTION_KEY_V" + entry.version()
            ));
        }

        if (keysByVersion.isEmpty() && !keyringProperties.encryptionKey().isBlank()) {
            keysByVersion.put(LEGACY_KEY_VERSION, decodeKey(
                    keyringProperties.encryptionKey(),
                    "BROKER_CREDENTIAL_ENCRYPTION_KEY"
            ));
        }

        this.secretKeysByVersion = Map.copyOf(keysByVersion);

        if (secretKeysByVersion.isEmpty()) {
            this.currentVersion = null;
            return;
        }

        Integer configuredCurrentVersion = keyringProperties.encryptionCurrentVersion();
        if (!secretKeysByVersion.containsKey(configuredCurrentVersion)) {
            throw new IllegalStateException(
                    "tradeguide.broker.encryption-current-version must reference a key present in "
                            + "tradeguide.broker.encryption-keys (or version 1 when only "
                            + "BROKER_CREDENTIAL_ENCRYPTION_KEY is set)."
            );
        }
        this.currentVersion = configuredCurrentVersion;
    }

    private static SecretKey decodeKey(String encodedKey, String sourceName) {
        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(sourceName + " must be Base64 encoded.", exception);
        }

        if (decodedKey.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(sourceName + " must contain exactly 32 bytes.");
        }

        return new SecretKeySpec(decodedKey, "AES");
    }

    @Override
    public boolean isConfigured() {
        return currentVersion != null;
    }

    @Override
    public EncryptedBrokerCredential encrypt(String plaintext) {
        requireConfigured();
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("암호화할 자격 증명이 필요합니다.");
        }

        byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH_BYTES];
        secureRandom.nextBytes(initializationVector);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeysByVersion.get(currentVersion), new GCMParameterSpec(
                    AUTHENTICATION_TAG_LENGTH_BITS,
                    initializationVector
            ));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedBrokerCredential(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(initializationVector),
                    currentVersion
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("증권사 자격 증명을 암호화하지 못했습니다.", exception);
        }
    }

    @Override
    public String decrypt(EncryptedBrokerCredential encryptedCredential) {
        requireConfigured();
        SecretKey secretKey = secretKeysByVersion.get(encryptedCredential.keyVersion());
        if (secretKey == null) {
            throw new IllegalStateException("증권사 자격 증명을 복호화하지 못했습니다.");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(
                    AUTHENTICATION_TAG_LENGTH_BITS,
                    Base64.getDecoder().decode(encryptedCredential.initializationVector())
            ));
            return new String(
                    cipher.doFinal(Base64.getDecoder().decode(encryptedCredential.ciphertext())),
                    StandardCharsets.UTF_8
            );
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("증권사 자격 증명을 복호화하지 못했습니다.", exception);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BrokerConnectionUnavailableException(
                    "증권사 연결 암호화 키가 설정되지 않았습니다."
            );
        }
    }
}
