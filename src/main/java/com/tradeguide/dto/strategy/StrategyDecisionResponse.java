package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.*;

import java.math.BigDecimal;

public class StrategyDecisionResponse {

    private final StrategyAction action;
    private final BigDecimal referencePrice;
    private final String reason;
    private final StrategyMetadataResponse metadata;
    private final StrategyTrend trend;
    private final StrategySignalEvent signalEvent;
    private final Integer weeksSinceCross;

    public StrategyDecisionResponse(
            StrategyAction action,
            BigDecimal referencePrice,
            String reason,
            StrategyMetadataResponse metadata
    ) {
        this(action, referencePrice, reason, metadata, null, null, null);
    }

    public StrategyDecisionResponse(
            StrategyAction action,
            BigDecimal referencePrice,
            String reason,
            StrategyMetadataResponse metadata,
            StrategyTrend trend,
            StrategySignalEvent signalEvent,
            Integer weeksSinceCross
    ) {
        this.action = action;
        this.referencePrice = referencePrice;
        this.reason = reason;
        this.metadata = metadata;
        this.trend = trend;
        this.signalEvent = signalEvent;
        this.weeksSinceCross = weeksSinceCross;
    }

    public static StrategyDecisionResponse from(StrategyDecision strategyDecision) {
        StrategySignal signal = strategyDecision.getSignal();

        return new StrategyDecisionResponse(
                strategyDecision.getAction(),
                signal.getReferencePrice(),
                strategyDecision.getReason(),
                StrategyMetadataResponse.from(signal.getMetadata()),
                signal.getTrend(),
                signal.getSignalEvent(),
                signal.getWeeksSinceCross()
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

    public Integer getWeeksSinceCross() {
        return weeksSinceCross;
    }
}
