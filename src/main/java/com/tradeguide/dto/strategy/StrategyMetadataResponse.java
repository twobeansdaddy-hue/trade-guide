package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.StrategyMetadata;

import java.time.LocalDate;
import java.util.List;

public class StrategyMetadataResponse {

    private final String strategyId;
    private final String strategyVersion;
    private final LocalDate dataAsOf;
    private final String confidence;
    private final List<String> caveats;

    public StrategyMetadataResponse(
            String strategyId,
            String strategyVersion,
            LocalDate dataAsOf
    ) {
        this(strategyId, strategyVersion, dataAsOf, null, List.of());
    }

    public StrategyMetadataResponse(
            String strategyId,
            String strategyVersion,
            LocalDate dataAsOf,
            String confidence,
            List<String> caveats
    ) {
        this.strategyId = strategyId;
        this.strategyVersion = strategyVersion;
        this.dataAsOf = dataAsOf;
        this.confidence = confidence;
        this.caveats = caveats == null ? List.of() : List.copyOf(caveats);
    }

    public static StrategyMetadataResponse from(StrategyMetadata strategyMetadata) {
        return new StrategyMetadataResponse(
                strategyMetadata.getStrategyId(),
                strategyMetadata.getStrategyVersion(),
                strategyMetadata.getDataAsOf(),
                strategyMetadata.getConfidence(),
                strategyMetadata.getCaveats()
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

    public String getConfidence() {
        return confidence;
    }

    public List<String> getCaveats() {
        return caveats;
    }
}
