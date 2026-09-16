package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.trade.Market;

public class AssetStrategyGuideResponse {
    private final Market market;
    private final String ticker;
    private final String displayName;
    private final StrategyDecisionResponse decision;

    public AssetStrategyGuideResponse(
            Market market,
            String ticker,
            String displayName,
            StrategyDecisionResponse decision
    ) {
        this.market = market;
        this.ticker = ticker;
        this.displayName = displayName;
        this.decision = decision;
    }

    public static AssetStrategyGuideResponse from(AssetStrategyGuide assetStrategyGuide, String displayName) {
        return new AssetStrategyGuideResponse(
                assetStrategyGuide.getMarket(),
                assetStrategyGuide.getTicker(),
                displayName,
                StrategyDecisionResponse.from(assetStrategyGuide.getStrategyDecision())
        );
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public String getDisplayName() {
        return displayName;
    }

    public StrategyDecisionResponse getDecision() {
        return decision;
    }
}
