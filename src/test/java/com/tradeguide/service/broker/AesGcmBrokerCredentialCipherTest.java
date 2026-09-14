package com.tradeguide.service.broker;

import com.tradeguide.config.BrokerCredentialKeyringProperties;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmBrokerCredentialCipherTest {

    @Test
    void encryptsAndDecryptsCredentialWithoutRetainingPlaintext() {
        AesGcmBrokerCredentialCipher cipher = new AesGcmBrokerCredentialCipher(legacyOnlyProperties(testKey(1)));

        EncryptedBrokerCredential encrypted = cipher.encrypt("test-client-secret");

        assertThat(encrypted.ciphertext()).doesNotContain("test-client-secret");
        assertThat(encrypted.initializationVector()).isNotBlank();
        assertThat(cipher.decrypt(encrypted)).isEqualTo("test-client-secret");
    }

    @Test
    void rejectsEncryptionWhenKeyIsNotConfigured() {
        AesGcmBrokerCredentialCipher cipher = new AesGcmBrokerCredentialCipher(legacyOnlyProperties(""));

        assertThatThrownBy(() -> cipher.encrypt("test-client-secret"))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("증권사 연결 암호화 키가 설정되지 않았습니다.");
    }

    @Test
    void fallsBackToLegacyKeyAsVersion1WhenKeyringIsUnset() {
        AesGcmBrokerCredentialCipher cipher = new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(testKey(1), List.of(), null)
        );

        EncryptedBrokerCredential encrypted = cipher.encrypt("test-client-secret");

        assertThat(encrypted.keyVersion()).isEqualTo(1);
        assertThat(cipher.decrypt(encrypted)).isEqualTo("test-client-secret");
    }

    @Test
    void encryptsWithCurrentVersionAndDecryptsOlderStoredVersion() {
        // 버전 1로 이미 저장된 행이 있다고 가정하고, 그 ciphertext를 별도 버전-1 전용
        // 암호기로 만든다.
        AesGcmBrokerCredentialCipher versionOneOnlyCipher = new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        "",
                        List.of(new BrokerCredentialKeyringProperties.KeyEntry(1, testKey(1))),
                        1
                )
        );
        EncryptedBrokerCredential storedWithVersion1 = versionOneOnlyCipher.encrypt("legacy-secret");

        // 키링에 버전 1과 2가 모두 있고 현재 버전은 2인 다중 키 암호기.
        AesGcmBrokerCredentialCipher keyringCipher = new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        "",
                        List.of(
                                new BrokerCredentialKeyringProperties.KeyEntry(1, testKey(1)),
                                new BrokerCredentialKeyringProperties.KeyEntry(2, testKey(2))
                        ),
                        2
                )
        );

        assertThat(keyringCipher.decrypt(storedWithVersion1)).isEqualTo("legacy-secret");

        EncryptedBrokerCredential encryptedNow = keyringCipher.encrypt("new-secret");
        assertThat(encryptedNow.keyVersion()).isEqualTo(2);
        assertThat(keyringCipher.decrypt(encryptedNow)).isEqualTo("new-secret");
    }

    @Test
    void rejectsDecryptionWhenStoredVersionKeyIsNoLongerConfigured() {
        AesGcmBrokerCredentialCipher versionOneOnlyCipher = new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        "",
                        List.of(new BrokerCredentialKeyringProperties.KeyEntry(1, testKey(1))),
                        1
                )
        );
        EncryptedBrokerCredential storedWithVersion1 = versionOneOnlyCipher.encrypt("legacy-secret");

        AesGcmBrokerCredentialCipher version2OnlyCipher = new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        "",
                        List.of(new BrokerCredentialKeyringProperties.KeyEntry(2, testKey(2))),
                        2
                )
        );

        assertThatThrownBy(() -> version2OnlyCipher.decrypt(storedWithVersion1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("증권사 자격 증명을 복호화하지 못했습니다.");
    }

    @Test
    void rejectsStartupWhenCurrentVersionKeyIsMissingFromKeyring() {
        assertThatThrownBy(() -> new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        "",
                        List.of(new BrokerCredentialKeyringProperties.KeyEntry(2, testKey(2))),
                        1
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tradeguide.broker.encryption-current-version");
    }

    @Test
    void rejectsMalformedKeyVersionEntry() {
        assertThatThrownBy(() -> new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        "",
                        List.of(new BrokerCredentialKeyringProperties.KeyEntry(1, "not-base64!!")),
                        1
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("BROKER_CREDENTIAL_ENCRYPTION_KEY_V1 must be Base64 encoded.");
    }

    @Test
    void rejectsKeyVersionEntryWithWrongLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        "",
                        List.of(new BrokerCredentialKeyringProperties.KeyEntry(1, shortKey)),
                        1
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("BROKER_CREDENTIAL_ENCRYPTION_KEY_V1 must contain exactly 32 bytes.");
    }

    @Test
    void rejectsStartupWhenNonblankKeyEntriesShareVersion() {
        assertThatThrownBy(() -> new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        "",
                        List.of(
                                new BrokerCredentialKeyringProperties.KeyEntry(1, testKey(1)),
                                new BrokerCredentialKeyringProperties.KeyEntry(1, testKey(2))
                        ),
                        1
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("tradeguide.broker.encryption-keys must not contain duplicate version 1.");
    }

    @Test
    void allowsDuplicateVersionWhenOnlyOneEntryIsNonblank() {
        AesGcmBrokerCredentialCipher cipher = new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        "",
                        List.of(
                                new BrokerCredentialKeyringProperties.KeyEntry(1, testKey(1)),
                                new BrokerCredentialKeyringProperties.KeyEntry(1, "")
                        ),
                        1
                )
        );

        assertThat(cipher.isConfigured()).isTrue();
    }

    @Test
    void rejectsKeyEntryWithZeroVersion() {
        assertThatThrownBy(() -> new BrokerCredentialKeyringProperties.KeyEntry(0, testKey(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tradeguide.broker.encryption-keys version must be a positive integer (>= 1) but was 0.");
    }

    @Test
    void rejectsKeyEntryWithNegativeVersion() {
        assertThatThrownBy(() -> new BrokerCredentialKeyringProperties.KeyEntry(-1, testKey(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tradeguide.broker.encryption-keys version must be a positive integer (>= 1) but was -1.");
    }

    private BrokerCredentialKeyringProperties legacyOnlyProperties(String legacyKey) {
        return new BrokerCredentialKeyringProperties(legacyKey, List.of(), 1);
    }

    private String testKey(int seed) {
        byte[] key = new byte[32];
        key[0] = (byte) seed;
        return Base64.getEncoder().encodeToString(key);
    }
}
