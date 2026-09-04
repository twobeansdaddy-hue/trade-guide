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
    }

    void assignBrokerConnection(BrokerConnection brokerConnection) {
        this.brokerConnection = brokerConnection;
    }

    public Long getId() { return id; }
    public BrokerConnection getBrokerConnection() { return brokerConnection; }
    public String getMaskedAccountNumber() { return maskedAccountNumber; }
    public String getAccountType() { return accountType; }

    /** 암호문만 반환한다. 복호화된 계좌 일련번호는 API 응답이나 로그에 노출하지 않는다. */
    public String getEncryptedAccountSequence() { return encryptedAccountSequence; }
    public String getAccountSequenceInitializationVector() { return accountSequenceInitializationVector; }
    public int getEncryptionKeyVersion() { return encryptionKeyVersion; }
}
