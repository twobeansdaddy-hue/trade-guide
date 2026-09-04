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

    protected BrokerAccount() {
    }

    public BrokerAccount(String encryptedAccountSequence, String initializationVector, String maskedAccountNumber, String accountType) {
        this.encryptedAccountSequence = encryptedAccountSequence;
        this.accountSequenceInitializationVector = initializationVector;
        this.maskedAccountNumber = maskedAccountNumber;
        this.accountType = accountType;
    }

    void assignBrokerConnection(BrokerConnection brokerConnection) {
        this.brokerConnection = brokerConnection;
    }

    public Long getId() { return id; }
    public String getMaskedAccountNumber() { return maskedAccountNumber; }
    public String getAccountType() { return accountType; }
}
