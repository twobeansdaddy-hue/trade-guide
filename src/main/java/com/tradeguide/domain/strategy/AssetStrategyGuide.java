package com.tradeguide.domain.strategy;

import com.tradeguide.domain.trade.Market;

public class AssetStrategyGuide {

    private final Market market;
    private final String ticker;
    private final StrategyDecision strategyDecision;

    public AssetStrategyGuide(
            Market market,
            String ticker,
            StrategyDecision strategyDecision
    ) {
        this.market = market;
        this.ticker = ticker;
        this.strategyDecision = strategyDecision;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public StrategyDecision getStrategyDecision() {
        return strategyDecision;
    }
}
