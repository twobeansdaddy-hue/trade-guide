package com.tradeguide.domain.strategy;

import java.math.BigDecimal;

public class StrategyDecision {

    private final StrategyAction action;
    private final BigDecimal referencePrice;
    private final String reason;
    private final StrategyMetadata metadata;
    private final StrategyTrend trend;
    private final StrategySignalEvent signalEvent;

    public StrategyDecision(
            StrategyAction action,
            BigDecimal referencePrice,
            String reason,
            StrategyMetadata metadata
    ) {
        this(action, referencePrice, reason, metadata, null, null);
    }

    public StrategyDecision(
            StrategyAction action,
            BigDecimal referencePrice,
            String reason,
            StrategyMetadata metadata,
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

    public StrategyAction getAction() {
        return action;
    }

    public BigDecimal getReferencePrice() {
        return referencePrice;
    }

    public String getReason() {
        return reason;
    }

    public StrategyMetadata getMetadata() {
        return metadata;
    }

    public StrategyTrend getTrend() {
        return trend;
    }

    public StrategySignalEvent getSignalEvent() {
        return signalEvent;
    }
}
