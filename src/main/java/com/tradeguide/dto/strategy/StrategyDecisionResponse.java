package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategySignalEvent;
import com.tradeguide.domain.strategy.StrategyTrend;

import java.math.BigDecimal;

public class StrategyDecisionResponse {

    private final StrategyAction action;
    private final BigDecimal referencePrice;
    private final String reason;
    private final StrategyMetadataResponse metadata;
    private final StrategyTrend trend;
    private final StrategySignalEvent signalEvent;

    public StrategyDecisionResponse(
            StrategyAction action,
            BigDecimal referencePrice,
            String reason,
            StrategyMetadataResponse metadata
    ) {
        this(action, referencePrice, reason, metadata, null, null);
    }

    public StrategyDecisionResponse(
            StrategyAction action,
            BigDecimal referencePrice,
            String reason,
            StrategyMetadataResponse metadata,
            StrategyTrend trend,
            StrategySignalEvent signalEvent
    ) {
        this.action = action;
        this.referencePrice = referencePrice;
        this.reason = reason;
        this.metadata = metadata;
        this.trend = trend;
        this.signalEvent = signalEvent;
    }

    public static StrategyDecisionResponse from(StrategyDecision strategyDecision) {
        return new StrategyDecisionResponse(
                strategyDecision.getAction(),
                strategyDecision.getReferencePrice(),
                strategyDecision.getReason(),
                StrategyMetadataResponse.from(strategyDecision.getMetadata()),
                strategyDecision.getTrend(),
                strategyDecision.getSignalEvent()
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

    public StrategyTrend getTrend() {
        return trend;
    }

    public StrategySignalEvent getSignalEvent() {
        return signalEvent;
    }
}
