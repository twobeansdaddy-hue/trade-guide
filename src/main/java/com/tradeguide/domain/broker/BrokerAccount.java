package com.tradeguide.domain.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 검증된 증권사 연결에서 발견한 계좌 행이다.
 *
 * <p>이 행의 식별자는 보유 종목 스냅샷과 포트폴리오 링크가 참조한다. 따라서 재검증에서
 * 증권사가 더 이상 반환하지 않는 계좌라도 행을 지우지 않고 {@link BrokerAccountStatus#DETACHED}로
 * 표시해 과거 이력의 참조 무결성을 지킨다.
 */
@Entity
@Table(name = "broker_accounts")
public class BrokerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_connection_id", nullable = false)
    private BrokerConnection brokerConnection;

    @Column(name = "encrypted_account_sequence", nullable = false, length = 1024)
    private String encryptedAccountSequence;

    @Column(name = "account_sequence_initialization_vector", nullable = false, length = 255)
    private String accountSequenceInitializationVector;

    @Column(name = "masked_account_number", nullable = false, length = 255)
    private String maskedAccountNumber;

    @Column(name = "account_type", nullable = false, length = 50)
    private String accountType;

    @Column(name = "encryption_key_version", nullable = false)
    private int encryptionKeyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BrokerAccountStatus status;

    /** 증권사가 이 계좌를 더 이상 반환하지 않게 된 시각이다. {@code ACTIVE}인 동안에는 비어 있다. */
    @Column(name = "detached_at")
    private LocalDateTime detachedAt;

    protected BrokerAccount() {
    }

    public BrokerAccount(
            String encryptedAccountSequence,
            String initializationVector,
            String maskedAccountNumber,
            String accountType,
            int encryptionKeyVersion
    ) {
        this.encryptedAccountSequence = encryptedAccountSequence;
        this.accountSequenceInitializationVector = initializationVector;
        this.maskedAccountNumber = maskedAccountNumber;
        this.accountType = accountType;
        this.encryptionKeyVersion = encryptionKeyVersion;
        this.status = BrokerAccountStatus.ACTIVE;
    }

    void assignBrokerConnection(BrokerConnection brokerConnection) {
        this.brokerConnection = brokerConnection;
    }

    /**
     * 재검증에서 다시 발견된 계좌의 암호문과 표시 정보를 최신 값으로 바꾸고 다시 활성으로 되돌린다.
     * 계좌 행의 식별자는 그대로 두므로 이 계좌를 참조하는 스냅샷과 승인 이력은 영향을 받지 않는다.
     */
    public void refreshFromProvider(
            String encryptedAccountSequence,
            String initializationVector,
            String maskedAccountNumber,
            String accountType,
            int encryptionKeyVersion
    ) {
        this.encryptedAccountSequence = encryptedAccountSequence;
        this.accountSequenceInitializationVector = initializationVector;
        this.maskedAccountNumber = maskedAccountNumber;
        this.accountType = accountType;
        this.encryptionKeyVersion = encryptionKeyVersion;
        this.status = BrokerAccountStatus.ACTIVE;
        this.detachedAt = null;
    }

    /**
     * 증권사가 더 이상 반환하지 않는 계좌로 표시한다. 이미 분리된 계좌는 최초 분리 시각을 유지한다.
     * 행을 지우지 않으므로 이 계좌를 참조하는 과거 스냅샷과 승인 이력은 그대로 남는다.
     */
    void detach(LocalDateTime detachedAt) {
        if (this.status == BrokerAccountStatus.DETACHED) {
            return;
        }

        this.status = BrokerAccountStatus.DETACHED;
        this.detachedAt = detachedAt;
    }

    /** 가장 최근 검증에서 증권사가 반환한 계좌인지 여부다. 연결 후보와 신규 링크는 이 값이 참인 계좌만 허용한다. */
    public boolean isActive() {
        return status == BrokerAccountStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public BrokerConnection getBrokerConnection() { return brokerConnection; }
    public String getMaskedAccountNumber() { return maskedAccountNumber; }
    public String getAccountType() { return accountType; }
    public BrokerAccountStatus getStatus() { return status; }
    public LocalDateTime getDetachedAt() { return detachedAt; }

    /** 암호문만 반환한다. 복호화된 계좌 일련번호는 API 응답이나 로그에 노출하지 않는다. */
    public String getEncryptedAccountSequence() { return encryptedAccountSequence; }
    public String getAccountSequenceInitializationVector() { return accountSequenceInitializationVector; }
    public int getEncryptionKeyVersion() { return encryptionKeyVersion; }
}
