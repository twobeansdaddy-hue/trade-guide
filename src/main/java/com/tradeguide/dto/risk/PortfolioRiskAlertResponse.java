package com.tradeguide.dto.risk;

import com.tradeguide.domain.risk.PortfolioRiskAlert;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PortfolioRiskAlertResponse {

    private final Market market;
    private final String ticker;
    private final BigDecimal exposureRate;
    private final BigDecimal maxExposureRate;
    private final String message;

    public PortfolioRiskAlertResponse(
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

    public static PortfolioRiskAlertResponse from(PortfolioRiskAlert alert) {
        return new PortfolioRiskAlertResponse(
                alert.getMarket(),
                alert.getTicker(),
                alert.getExposureRate().setScale(2, RoundingMode.HALF_UP),
                alert.getMaxExposureRate().setScale(2, RoundingMode.HALF_UP),
                alert.getMessage()
        );
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
