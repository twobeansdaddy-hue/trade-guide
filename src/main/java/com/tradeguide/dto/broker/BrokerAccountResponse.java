package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerAccount;

public record BrokerAccountResponse(Long id, String maskedAccountNumber, String accountType) {
    static BrokerAccountResponse from(BrokerAccount account) {
        return new BrokerAccountResponse(account.getId(), account.getMaskedAccountNumber(), account.getAccountType());
    }
}
