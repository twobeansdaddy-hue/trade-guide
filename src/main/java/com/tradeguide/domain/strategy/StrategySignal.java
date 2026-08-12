package com.tradeguide.domain.strategy;

import java.math.BigDecimal;

public class StrategySignal {

    private final BigDecimal referencePrice;
    private final String reason;
    private final StrategyMetadata metadata;
    private final StrategyTrend trend;
    private final StrategySignalEvent signalEvent;
    private final Integer weeksSinceCross;

    public StrategySignal(
            BigDecimal referencePrice,
            String reason,
            StrategyMetadata metadata,
            StrategyTrend trend,
            StrategySignalEvent signalEvent,
            Integer weeksSinceCross
    ) {
        this.referencePrice = referencePrice;
        this.reason = reason;
        this.metadata = metadata;
        this.trend = trend;
        this.signalEvent = signalEvent;
        this.weeksSinceCross = weeksSinceCross;
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

    public Integer getWeeksSinceCross() {
        return weeksSinceCross;
    }
}
