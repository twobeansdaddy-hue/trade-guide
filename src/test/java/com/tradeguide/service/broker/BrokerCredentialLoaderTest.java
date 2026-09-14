package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 자격 증명 복호화의 유일한 지점을 검증한다. 항목 수를 모르는 채로 저장된 값을 전부
 * 복호화해 돌려줘야 두 번째 증권사가 세 번째 항목을 요구해도 이 코드가 그대로 쓰인다.
 */
class BrokerCredentialLoaderTest {

    private final BrokerCredentialLoader loader = new BrokerCredentialLoader(new ReversibleCipher());

    @Test
    void decryptsEveryStoredCredentialFieldRegardlessOfHowManyThereAre() {
        BrokerConnection connection = connectionWith(
                new BrokerConnectionSecretValue("clientId", "enc:client-id", "iv-1", 1),
                new BrokerConnectionSecretValue("clientSecret", "enc:client-secret", "iv-2", 1),
                new BrokerConnectionSecretValue("apiPassphrase", "enc:passphrase", "iv-3", 2)
        );

        BrokerCredentials credentials = loader.load(connection);

        assertThat(credentials.keys()).containsExactlyInAnyOrder("clientId", "clientSecret", "apiPassphrase");
        assertThat(credentials.require("clientId")).isEqualTo("client-id");
        assertThat(credentials.require("clientSecret")).isEqualTo("client-secret");
        assertThat(credentials.require("apiPassphrase")).isEqualTo("passphrase");
    }

    /** 키 버전은 항목마다 다를 수 있다. 무중단 로테이션 중에는 한 연결 안에서 섞인다. */
    @Test
    void decryptsFieldsThatWereEncryptedUnderDifferentKeyVersions() {
        BrokerConnection connection = connectionWith(
                new BrokerConnectionSecretValue("clientId", "enc:old", "iv-1", 1),
                new BrokerConnectionSecretValue("clientSecret", "enc:new", "iv-2", 7)
        );

        BrokerCredentials credentials = loader.load(connection);

        assertThat(credentials.require("clientId")).isEqualTo("old");
        assertThat(credentials.require("clientSecret")).isEqualTo("new");
    }

    @Test
    void failsWhenConnectionHasNoStoredCredentials() {
        BrokerConnection connection = new BrokerConnection(
                new Member("broker@example.com", "broker-user"),
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권"
        );

        assertThatThrownBy(() -> loader.load(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("증권사 자격 증명이 저장되어 있지 않습니다.");
    }

    @Test
    void decryptsAccountSequenceSeparatelyFromCredentials() {
        BrokerAccount account = new BrokerAccount("enc:12345", "iv", "*****1234", "위탁", 1);

        assertThat(loader.loadAccountSequence(account)).isEqualTo("12345");
    }

    private BrokerConnection connectionWith(BrokerConnectionSecretValue... values) {
        BrokerConnection connection = new BrokerConnection(
                new Member("broker@example.com", "broker-user"),
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권"
        );
        connection.replaceSecretValues(List.of(values));
        return connection;
    }

    /** 접두사만 떼는 가역 대역이다. 실제 암호화 키를 테스트에서 쓰지 않는다. */
    private static final class ReversibleCipher implements BrokerCredentialCipher {

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public EncryptedBrokerCredential encrypt(String plaintext) {
            return new EncryptedBrokerCredential("enc:" + plaintext, "iv", 1);
        }

        @Override
        public String decrypt(EncryptedBrokerCredential encryptedCredential) {
            return encryptedCredential.ciphertext().substring("enc:".length());
        }
    }
}
