package com.tradeguide.domain.strategy;

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

    public StrategyAction getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public StrategySignal getSignal() {
        return signal;
    }
}
