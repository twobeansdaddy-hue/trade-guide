package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;

public class AssetProfileResponse {

    private final Long id;
    private final Market market;
    private final String ticker;
    private final InvestmentTrack investmentTrack;

    public AssetProfileResponse(
            Long id,
            Market market,
            String ticker,
            InvestmentTrack investmentTrack) {
        this.id = id;
        this.market = market;
        this.ticker = ticker;
        this.investmentTrack = investmentTrack;
    }

    public static AssetProfileResponse from(AssetProfile assetProfile) {
        return new AssetProfileResponse(
                assetProfile.getId(),
                assetProfile.getMarket(),
                assetProfile.getTicker(),
                assetProfile.getInvestmentTrack()
        );
    }

    public Long getId() {
        return id;
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
