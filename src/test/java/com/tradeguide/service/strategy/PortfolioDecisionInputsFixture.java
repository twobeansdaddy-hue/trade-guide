package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PremarketGuideCandidateSource;
import com.tradeguide.domain.strategy.PremarketGuidePortfolioStateDigests;
import com.tradeguide.service.strategy.PortfolioDecisionInputs.AssetKey;
import com.tradeguide.service.strategy.PortfolioDecisionInputs.CandidateRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class PortfolioDecisionInputsFixture {

    private PortfolioDecisionInputsFixture() {
    }

    static PortfolioDecisionInputs inputs(
            List<Holding> holdings,
            Map<AssetKey, InvestmentTrack> strategyOverrides,
            BigDecimal portfolioStopLossRatio,
            Map<AssetKey, BigDecimal> stopLossOverrides,
            List<CandidateRef> candidates,
            boolean brokerSnapshotHasItems
    ) {
        String hash = "a".repeat(64);
        PremarketGuideCandidateSource source = PremarketGuideCandidateSource.PORTFOLIO;
        return new PortfolioDecisionInputs(10L, holdings, strategyOverrides, portfolioStopLossRatio,
                stopLossOverrides, source, candidates, brokerSnapshotHasItems,
                new PremarketGuidePortfolioStateDigests(1, Instant.parse("2026-09-14T12:59:00Z"),
                        hash, holdings.size(), hash, hash, hash, source, hash, hash, null, hash));
    }
}
