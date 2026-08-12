package com.tradeguide.domain.strategy;

import java.time.LocalDate;

public class StrategyMetadata {

    private final String strategyId;
    private final String strategyVersion;
    private final LocalDate dataAsOf;

    public StrategyMetadata(
            String strategyId,
            String strategyVersion,
            LocalDate dataAsOf
    ) {
        this.strategyId = strategyId;
        this.strategyVersion = strategyVersion;
        this.dataAsOf = dataAsOf;
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
