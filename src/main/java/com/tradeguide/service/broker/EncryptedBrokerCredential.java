package com.tradeguide.service.broker;

public record EncryptedBrokerCredential(
        String ciphertext,
        String initializationVector,
        int keyVersion
) {
}
