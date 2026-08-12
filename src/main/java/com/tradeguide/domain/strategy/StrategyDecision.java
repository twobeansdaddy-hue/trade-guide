package com.tradeguide.domain.strategy;

import java.math.BigDecimal;

public class StrategyDecision {

    private final StrategyAction action;
    private final String reason;
    private final StrategySignal signal;

    public StrategyDecision(
            StrategyAction action,
            String reason,
            StrategySignal signal
    ) {
        this.action = action;
        this.reason = reason;
        this.signal = signal;
    }

    public StrategyDecision(
            StrategyAction action,
            BigDecimal referencePrice,
            String reason,
            StrategyMetadata metadata
    ) {
        this(
                action,
                reason,
                new StrategySignal(
                        referencePrice,
                        reason,
                        metadata,
                        null,
                        null,
                        null
                )
        );
    }

    public StrategyDecision(
            StrategyAction action,
            BigDecimal referencePrice,
            String reason,
            StrategyMetadata metadata,
            StrategyTrend trend,
            StrategySignalEvent signalEvent
    ) {
        this(
                action,
                reason,
                new StrategySignal(
                        referencePrice,
                        reason,
                        metadata,
                        trend,
                        signalEvent,
                        null
                )
        );
    }

    public StrategyAction getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public StrategySignal getSignal() {
        return signal;
    }

    public BigDecimal getReferencePrice() {
        return signal.getReferencePrice();
    }

    public StrategyMetadata getMetadata() {
        return signal.getMetadata();
    }

    public StrategyTrend getTrend() {
        return signal.getTrend();
    }

    public StrategySignalEvent getSignalEvent() {
        return signal.getSignalEvent();
    }

    public Integer getWeeksSinceCross() {
        return signal.getWeeksSinceCross();
    }
}
