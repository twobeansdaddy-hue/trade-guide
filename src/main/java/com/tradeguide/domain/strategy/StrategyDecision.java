package com.tradeguide.domain.strategy;

public class StrategyDecision {

    private final StrategyAction action;
    private final String reason;
    private final StrategySignal signal;
    private final StrategyDecisionGuidance guidance;

    public StrategyDecision(
            StrategyAction action,
            String reason,
            StrategySignal signal
    ) {
        this(action, reason, signal, StrategyDecisionGuidance.unknown());
    }

    public StrategyDecision(
            StrategyAction action,
            String reason,
            StrategySignal signal,
            StrategyDecisionGuidance guidance
    ) {
        this.action = action;
        this.reason = reason;
        this.signal = signal;
        this.guidance = guidance == null ? StrategyDecisionGuidance.unknown() : guidance;
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

    public StrategyDecisionGuidance getGuidance() {
        return guidance;
    }
}
