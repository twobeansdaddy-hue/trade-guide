package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.StrategyDecisionGuidance;

import java.math.BigDecimal;

public class StrategyDecisionGuidanceResponse {

    private final String entryTimingStatus;
    private final String entryTimingMessage;
    private final String stopLossStatus;
    private final BigDecimal stopLossRatio;
    private final BigDecimal stopLossPrice;
    private final String stopLossMessage;

    private StrategyDecisionGuidanceResponse(StrategyDecisionGuidance guidance) {
        this.entryTimingStatus = guidance.getEntryTimingStatus();
        this.entryTimingMessage = guidance.getEntryTimingMessage();
        this.stopLossStatus = guidance.getStopLossStatus();
        this.stopLossRatio = guidance.getStopLossRatio();
        this.stopLossPrice = guidance.getStopLossPrice();
        this.stopLossMessage = guidance.getStopLossMessage();
    }

    public static StrategyDecisionGuidanceResponse from(StrategyDecisionGuidance guidance) {
        return new StrategyDecisionGuidanceResponse(
                guidance == null ? StrategyDecisionGuidance.unknown() : guidance
        );
    }

    public String getEntryTimingStatus() {
        return entryTimingStatus;
    }

    public String getEntryTimingMessage() {
        return entryTimingMessage;
    }

    public String getStopLossStatus() {
        return stopLossStatus;
    }

    public BigDecimal getStopLossRatio() {
        return stopLossRatio;
    }

    public BigDecimal getStopLossPrice() {
        return stopLossPrice;
    }

    public String getStopLossMessage() {
        return stopLossMessage;
    }
}
