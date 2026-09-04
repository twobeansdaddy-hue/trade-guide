package com.tradeguide.service.broker;

import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmBrokerCredentialCipherTest {

    @Test
    void encryptsAndDecryptsCredentialWithoutRetainingPlaintext() {
        AesGcmBrokerCredentialCipher cipher = new AesGcmBrokerCredentialCipher(testKey());

        EncryptedBrokerCredential encrypted = cipher.encrypt("test-client-secret");

        assertThat(encrypted.ciphertext()).doesNotContain("test-client-secret");
        assertThat(encrypted.initializationVector()).isNotBlank();
        assertThat(cipher.decrypt(encrypted)).isEqualTo("test-client-secret");
    }

    @Test
    void rejectsEncryptionWhenKeyIsNotConfigured() {
        AesGcmBrokerCredentialCipher cipher = new AesGcmBrokerCredentialCipher("");

        assertThatThrownBy(() -> cipher.encrypt("test-client-secret"))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("증권사 연결 암호화 키가 설정되지 않았습니다.");
    }

    private String testKey() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }
}
