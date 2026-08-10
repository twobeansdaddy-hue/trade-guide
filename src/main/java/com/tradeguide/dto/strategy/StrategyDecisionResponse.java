package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;

import java.math.BigDecimal;

public class StrategyDecisionResponse {

    private final StrategyAction action;
    private final BigDecimal referencePrice;
    private final String reason;
    private final StrategyMetadataResponse metadata;

    public StrategyDecisionResponse(
            StrategyAction action,
            BigDecimal referencePrice,
            String reason,
            StrategyMetadataResponse metadata
    ) {
        this.action = action;
        this.referencePrice = referencePrice;
        this.reason = reason;
        this.metadata = metadata;
    }

    public static StrategyDecisionResponse from(StrategyDecision strategyDecision) {
        return new StrategyDecisionResponse(
                strategyDecision.getAction(),
                strategyDecision.getReferencePrice(),
                strategyDecision.getReason(),
                StrategyMetadataResponse.from(strategyDecision.getMetadata())
        );
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

    public StrategyMetadataResponse getMetadata() {
        return metadata;
    }
}
