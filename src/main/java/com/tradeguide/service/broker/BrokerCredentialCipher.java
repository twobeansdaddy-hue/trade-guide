package com.tradeguide.service.broker;

public interface BrokerCredentialCipher {
    boolean isConfigured();

    EncryptedBrokerCredential encrypt(String plaintext);

    String decrypt(EncryptedBrokerCredential encryptedCredential);
}
