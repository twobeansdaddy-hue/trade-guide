package com.tradeguide.domain.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 증권사 연결 자격 증명 한 항목의 암호문이다. 제공자마다 필드 수가 다르므로
 * 열이 아니라 행으로 담는다. 필드가 늘어도 스키마는 그대로다.
 *
 * <p>{@link #getFieldKey()}는 {@link BrokerCredentialField#key()}와 같은 값이다. 진실의
 * 원천은 제공자 명세(enum)이고 이 표는 값만 담으므로 외래키를 걸 수 없다. 대신 저장 전에
 * 서비스가 명세와 대조해 화이트리스트 밖의 키를 거부한다. 그렇게 하지 않으면 클라이언트가
 * 임의 키를 만들어 이 표를 자유 사전처럼 쓸 수 있다.
 *
 * <p>{@code encryption_key_version}은 연결 단위가 아니라 <b>값 단위</b>로 둔다. 한 연결
 * 안에서 필드마다 키 버전이 다를 수 있어야 무중단 키 로테이션이 가능하다.
 *
 * <p>이 엔티티는 평문을 절대 담지 않는다. 복호화는
 * {@link com.tradeguide.service.broker.BrokerCredentialLoader} 한곳에서만 수행한다.
 */
@Entity
@Table(
        name = "broker_connection_secret_values",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_broker_connection_secret_values_field",
                columnNames = {"broker_connection_id", "field_key"}
        )
)
public class BrokerConnectionSecretValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_connection_id", nullable = false)
    private BrokerConnection brokerConnection;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Column(name = "ciphertext", nullable = false, length = 4096)
    private String ciphertext;

    @Column(name = "initialization_vector", nullable = false, length = 255)
    private String initializationVector;

    @Column(name = "encryption_key_version", nullable = false)
    private int encryptionKeyVersion;

    protected BrokerConnectionSecretValue() {
    }

    public BrokerConnectionSecretValue(
            String fieldKey,
            String ciphertext,
            String initializationVector,
            int encryptionKeyVersion
    ) {
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("자격 증명 항목 키는 필수입니다.");
        }
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("암호화된 자격 증명 값은 필수입니다.");
        }
        if (initializationVector == null || initializationVector.isBlank()) {
            throw new IllegalArgumentException("자격 증명 초기화 벡터는 필수입니다.");
        }

        this.fieldKey = fieldKey;
        this.ciphertext = ciphertext;
        this.initializationVector = initializationVector;
        this.encryptionKeyVersion = encryptionKeyVersion;
    }

    void assignBrokerConnection(BrokerConnection brokerConnection) {
        this.brokerConnection = brokerConnection;
    }

    public Long getId() {
        return id;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public String getInitializationVector() {
        return initializationVector;
    }

    public int getEncryptionKeyVersion() {
        return encryptionKeyVersion;
    }
}
