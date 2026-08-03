package com.tradeguide.domain.market;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.time.Instant;

public class MarketPrice {

    private final Market market;
    private final String ticker;
    private final BigDecimal currentPrice;
    private final Instant capturedAt;

    public MarketPrice(
            Market market,
            String ticker,
            BigDecimal currentPrice,
            Instant capturedAt
    ) {
        this.market = market;
        this.ticker = ticker;
        this.currentPrice = currentPrice;
        this.capturedAt = capturedAt;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}