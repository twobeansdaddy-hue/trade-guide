package com.tradeguide.dto.broker;

import jakarta.validation.constraints.NotNull;

public class PortfolioBrokerLinkUpdateRequest {

    @NotNull(message = "증권사 계좌 선택은 필수입니다.")
    private Long brokerAccountId;

    public Long getBrokerAccountId() {
        return brokerAccountId;
    }
}
