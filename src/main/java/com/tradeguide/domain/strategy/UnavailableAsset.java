package com.tradeguide.domain.strategy;

import com.tradeguide.domain.trade.Market;

public class UnavailableAsset {

    private final Market market;
    private final String ticker;
    private final String message;

    public UnavailableAsset(
            Market market,
            String ticker,
            String message) {
        this.market = market;
        this.ticker = ticker;
        this.message = message;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public String getMessage() {
        return message;
    }
}
