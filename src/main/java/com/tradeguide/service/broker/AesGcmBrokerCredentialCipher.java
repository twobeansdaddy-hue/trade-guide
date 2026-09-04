package com.tradeguide.service.broker;

import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesGcmBrokerCredentialCipher implements BrokerCredentialCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int INITIALIZATION_VECTOR_LENGTH_BYTES = 12;
    private static final int AUTHENTICATION_TAG_LENGTH_BITS = 128;
    private static final int KEY_VERSION = 1;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmBrokerCredentialCipher(
            @Value("${tradeguide.broker.encryption-key:}") String encodedEncryptionKey
    ) {
        if (encodedEncryptionKey.isBlank()) {
            this.secretKey = null;
            return;
        }

        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(encodedEncryptionKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("BROKER_CREDENTIAL_ENCRYPTION_KEY must be Base64 encoded.", exception);
        }

        if (decodedKey.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("BROKER_CREDENTIAL_ENCRYPTION_KEY must contain exactly 32 bytes.");
        }

        this.secretKey = new SecretKeySpec(decodedKey, "AES");
    }

    @Override
    public boolean isConfigured() {
        return secretKey != null;
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
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(
                    AUTHENTICATION_TAG_LENGTH_BITS,
                    initializationVector
            ));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedBrokerCredential(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(initializationVector),
                    KEY_VERSION
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("증권사 자격 증명을 암호화하지 못했습니다.", exception);
        }
    }

    @Override
    public String decrypt(EncryptedBrokerCredential encryptedCredential) {
        requireConfigured();
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
