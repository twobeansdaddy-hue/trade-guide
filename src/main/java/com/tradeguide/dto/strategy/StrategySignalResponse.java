package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.StrategySignalEvent;
import com.tradeguide.domain.strategy.StrategyTrend;

import java.math.BigDecimal;

public class StrategySignalResponse {

    private final BigDecimal referencePrice;
    private final String reason;
    private final StrategyMetadataResponse metadata;
    private final StrategyTrend trend;
    private final StrategySignalEvent signalEvent;
    private final Integer weeksSinceCross;

    public StrategySignalResponse(
            BigDecimal referencePrice,
            String reason,
            StrategyMetadataResponse metadata,
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

    public static StrategySignalResponse from(StrategySignal signal) {
        return new StrategySignalResponse(
                signal.getReferencePrice(),
                signal.getReason(),
                StrategyMetadataResponse.from(signal.getMetadata()),
                signal.getTrend(),
                signal.getSignalEvent(),
                signal.getWeeksSinceCross()
        );
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
