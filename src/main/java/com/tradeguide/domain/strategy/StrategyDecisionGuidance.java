package com.tradeguide.domain.strategy;

import java.math.BigDecimal;

public class StrategyDecisionGuidance {

    private final String entryTimingStatus;
    private final String entryTimingMessage;
    private final String stopLossStatus;
    private final BigDecimal stopLossRatio;
    private final BigDecimal stopLossPrice;
    private final String stopLossMessage;

    public StrategyDecisionGuidance(
            String entryTimingStatus,
            String entryTimingMessage,
            String stopLossStatus,
            BigDecimal stopLossRatio,
            BigDecimal stopLossPrice,
            String stopLossMessage
    ) {
        if (entryTimingStatus == null || entryTimingStatus.isBlank()) {
            throw new IllegalArgumentException("매수 시점 상태는 필수입니다.");
        }
        if (entryTimingMessage == null || entryTimingMessage.isBlank()) {
            throw new IllegalArgumentException("매수 시점 설명은 필수입니다.");
        }
        if (stopLossStatus == null || stopLossStatus.isBlank()) {
            throw new IllegalArgumentException("손절 상태는 필수입니다.");
        }
        if (stopLossRatio != null
                && (stopLossRatio.compareTo(BigDecimal.ZERO) <= 0
                || stopLossRatio.compareTo(BigDecimal.ONE) >= 0)) {
            throw new IllegalArgumentException("손절 기준 비율은 0보다 크고 1보다 작아야 합니다.");
        }
        if (stopLossPrice != null && stopLossPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("손절가는 0보다 커야 합니다.");
        }
        if (stopLossMessage == null || stopLossMessage.isBlank()) {
            throw new IllegalArgumentException("손절 설명은 필수입니다.");
        }

        this.entryTimingStatus = entryTimingStatus;
        this.entryTimingMessage = entryTimingMessage;
        this.stopLossStatus = stopLossStatus;
        this.stopLossRatio = stopLossRatio;
        this.stopLossPrice = stopLossPrice;
        this.stopLossMessage = stopLossMessage;
    }

    public static StrategyDecisionGuidance unknown() {
        return new StrategyDecisionGuidance(
                "UNKNOWN",
                "매수 시점 정보를 확인할 수 없습니다.",
                "NOT_CONFIGURED",
                null,
                null,
                "검증된 손절 규칙이 설정되지 않아 손절가를 자동 산출하지 않습니다."
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
