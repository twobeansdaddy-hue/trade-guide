package com.tradeguide.domain.strategy;

import java.math.BigDecimal;

public class StrategyDecision {

    private final StrategyAction action;
    private final BigDecimal referencePrice;
    private final String reason;

    public StrategyDecision(
            StrategyAction action,
            BigDecimal referencePrice,
            String reason) {
        this.action = action;
        this.referencePrice = referencePrice;
        this.reason = reason;
    }

    public StrategyAction getAction() {
        return action;
    }

    public BigDecimal getReferencePrice() {
        return referencePrice;
    }

    public String getReason() {
        return reason;
    }
}
