package com.tradeguide.domain.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "broker_connection_secrets")
public class BrokerConnectionSecret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_connection_id", nullable = false, unique = true)
    private BrokerConnection brokerConnection;

    @Column(name = "encrypted_client_id", nullable = false, length = 4096)
    private String encryptedClientId;

    @Column(name = "client_id_initialization_vector", nullable = false, length = 255)
    private String clientIdInitializationVector;

    @Column(name = "encrypted_client_secret", nullable = false, length = 4096)
    private String encryptedClientSecret;

    @Column(name = "client_secret_initialization_vector", nullable = false, length = 255)
    private String clientSecretInitializationVector;

    @Column(name = "encryption_key_version", nullable = false)
    private int encryptionKeyVersion;

    protected BrokerConnectionSecret() {
    }

    public BrokerConnectionSecret(
            String encryptedClientId,
            String clientIdInitializationVector,
            String encryptedClientSecret,
            String clientSecretInitializationVector,
            int encryptionKeyVersion
    ) {
        this.encryptedClientId = encryptedClientId;
        this.clientIdInitializationVector = clientIdInitializationVector;
        this.encryptedClientSecret = encryptedClientSecret;
        this.clientSecretInitializationVector = clientSecretInitializationVector;
        this.encryptionKeyVersion = encryptionKeyVersion;
    }

    void assignBrokerConnection(BrokerConnection brokerConnection) {
        this.brokerConnection = brokerConnection;
    }

    public String getEncryptedClientId() {
        return encryptedClientId;
    }

    public String getClientIdInitializationVector() {
        return clientIdInitializationVector;
    }

    public String getEncryptedClientSecret() {
        return encryptedClientSecret;
    }

    public String getClientSecretInitializationVector() {
        return clientSecretInitializationVector;
    }

    public int getEncryptionKeyVersion() {
        return encryptionKeyVersion;
    }
}
