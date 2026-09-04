package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.broker.BrokerProvider;

import java.time.LocalDateTime;
import java.util.List;

public class BrokerConnectionResponse {

    private final Long id;
    private final BrokerProvider provider;
    private final String displayName;
    private final BrokerConnectionStatus status;
    private final String maskedAccountLabel;
    private final LocalDateTime lastVerifiedAt;
    private final LocalDateTime createdAt;
    private final List<BrokerAccountResponse> accounts;

    private BrokerConnectionResponse(
            Long id,
            BrokerProvider provider,
            String displayName,
            BrokerConnectionStatus status,
            String maskedAccountLabel,
            LocalDateTime lastVerifiedAt,
            LocalDateTime createdAt,
            List<BrokerAccountResponse> accounts
    ) {
        this.id = id;
        this.provider = provider;
        this.displayName = displayName;
        this.status = status;
        this.maskedAccountLabel = maskedAccountLabel;
        this.lastVerifiedAt = lastVerifiedAt;
        this.createdAt = createdAt;
        this.accounts = accounts;
    }

    public static BrokerConnectionResponse from(BrokerConnection connection) {
        return new BrokerConnectionResponse(
                connection.getId(),
                connection.getProvider(),
                connection.getDisplayName(),
                connection.getStatus(),
                connection.getMaskedAccountLabel(),
                connection.getLastVerifiedAt(),
                connection.getCreatedAt(),
                connection.getAccounts().stream().map(BrokerAccountResponse::from).toList()
        );
    }

    public Long getId() {
        return id;
    }

    public BrokerProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BrokerConnectionStatus getStatus() {
        return status;
    }

    public String getMaskedAccountLabel() {
        return maskedAccountLabel;
    }

    public LocalDateTime getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<BrokerAccountResponse> getAccounts() { return accounts; }
}
