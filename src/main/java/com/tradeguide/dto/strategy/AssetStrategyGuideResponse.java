package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.trade.Market;

public class AssetStrategyGuideResponse {
    private final Market market;
    private final String ticker;
    private final StrategyDecisionResponse decision;

    public AssetStrategyGuideResponse(
            Market market,
            String ticker,
            StrategyDecisionResponse decision
    ) {
        this.market = market;
        this.ticker = ticker;
        this.decision = decision;
    }

    public static AssetStrategyGuideResponse from(AssetStrategyGuide assetStrategyGuide) {
        return new AssetStrategyGuideResponse(
                assetStrategyGuide.getMarket(),
                assetStrategyGuide.getTicker(),
                StrategyDecisionResponse.from(assetStrategyGuide.getStrategyDecision())
        );
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public StrategyDecisionResponse getDecision() {
        return decision;
    }
}
