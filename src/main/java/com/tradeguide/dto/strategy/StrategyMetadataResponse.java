package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.StrategyMetadata;

import java.time.LocalDate;

public class StrategyMetadataResponse {

    private final String strategyId;
    private final String strategyVersion;
    private final LocalDate dataAsOf;

    public StrategyMetadataResponse(
            String strategyId,
            String strategyVersion,
            LocalDate dataAsOf
    ) {
        this.strategyId = strategyId;
        this.strategyVersion = strategyVersion;
        this.dataAsOf = dataAsOf;
    }

    public static StrategyMetadataResponse from(StrategyMetadata strategyMetadata) {
        return new StrategyMetadataResponse(
                strategyMetadata.getStrategyId(),
                strategyMetadata.getStrategyVersion(),
                strategyMetadata.getDataAsOf()
        );
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getStrategyVersion() {
        return strategyVersion;
    }

    public LocalDate getDataAsOf() {
        return dataAsOf;
    }
}
