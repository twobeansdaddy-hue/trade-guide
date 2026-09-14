package com.tradeguide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 증권사 자격 증명 암호화 키 설정을 값이 아니라 이름만 바인딩한다. 실제 Base64 키 값은
 * 환경변수({@code BROKER_CREDENTIAL_ENCRYPTION_KEY}, {@code BROKER_CREDENTIAL_ENCRYPTION_KEY_V1},
 * ...)로만 주입되며 이 클래스와 {@code application.yml}은 이름만 참조한다.
 *
 * <p>{@code encryptionKeys}가 비어 있으면(값이 있는 항목이 하나도 없으면)
 * {@code encryptionKey} 하나만으로 버전 1 키를 구성해 기존 배포와 하위 호환한다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §1 결정 2).
 */
@ConfigurationProperties(prefix = "tradeguide.broker")
public record BrokerCredentialKeyringProperties(
        String encryptionKey,
        List<KeyEntry> encryptionKeys,
        Integer encryptionCurrentVersion
) {
    public BrokerCredentialKeyringProperties {
        encryptionKey = encryptionKey == null ? "" : encryptionKey;
        encryptionKeys = encryptionKeys == null ? List.of() : List.copyOf(encryptionKeys);
        encryptionCurrentVersion = encryptionCurrentVersion == null ? 1 : encryptionCurrentVersion;
    }

    public record KeyEntry(int version, String value) {
        public KeyEntry {
            value = value == null ? "" : value;
            if (version < 1) {
                throw new IllegalStateException(
                        "tradeguide.broker.encryption-keys version must be a positive integer (>= 1) but was "
                                + version + "."
                );
            }
        }
    }
}
