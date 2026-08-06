package com.tradeguide.domain.strategy;

import com.tradeguide.domain.trade.Market;

public class AssetProfile {
    private final Market market;
    private final String ticker;
    private final InvestmentTrack investmentTrack;

    public AssetProfile(
            Market market,
            String ticker,
            InvestmentTrack investmentTrack) {
        this.market = market;
        this.ticker = ticker;
        this.investmentTrack = investmentTrack;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public InvestmentTrack getInvestmentTrack() {
        return investmentTrack;
    }
}
