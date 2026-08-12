package com.tradeguide.domain.risk;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

public class HoldingExposure {

    private final Market market;
    private final String ticker;
    private final BigDecimal marketValue;
    private final BigDecimal exposureRate;

    public HoldingExposure(
            Market market,
            String ticker,
            BigDecimal marketValue,
            BigDecimal exposureRate
    ) {
        this.market = market;
        this.ticker = ticker;
        this.marketValue = marketValue;
        this.exposureRate = exposureRate;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public BigDecimal getExposureRate() {
        return exposureRate;
    }
}
