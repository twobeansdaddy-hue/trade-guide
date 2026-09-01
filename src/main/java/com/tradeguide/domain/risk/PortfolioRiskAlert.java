package com.tradeguide.domain.risk;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

public class PortfolioRiskAlert {

    private final Market market;
    private final String ticker;
    private final BigDecimal exposureRate;
    private final BigDecimal maxExposureRate;
    private final String message;

    public PortfolioRiskAlert(
            Market market,
            String ticker,
            BigDecimal exposureRate,
            BigDecimal maxExposureRate,
            String message
    ) {
        this.market = market;
        this.ticker = ticker;
        this.exposureRate = exposureRate;
        this.maxExposureRate = maxExposureRate;
        this.message = message;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getExposureRate() {
        return exposureRate;
    }

    public BigDecimal getMaxExposureRate() {
        return maxExposureRate;
    }

    public String getMessage() {
        return message;
    }
}
