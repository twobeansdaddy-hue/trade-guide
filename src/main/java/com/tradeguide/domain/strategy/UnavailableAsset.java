package com.tradeguide.domain.strategy;

import com.tradeguide.domain.trade.Market;

public class UnavailableAsset {

    private final Market market;
    private final String ticker;
    private final String message;
    private final StrategyGuideUnavailableReason reason;

    public UnavailableAsset(
            Market market,
            String ticker,
            String message) {
        this(market, ticker, message, null);
    }

    public UnavailableAsset(
            Market market,
            String ticker,
            String message,
            StrategyGuideUnavailableReason reason) {
        this.market = market;
        this.ticker = ticker;
        this.message = message;
        this.reason = reason;
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

    public StrategyGuideUnavailableReason getReason() {
        return reason;
    }
}
