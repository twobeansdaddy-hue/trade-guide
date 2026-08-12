package com.tradeguide.dto.risk;

import com.tradeguide.domain.risk.HoldingExposure;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class HoldingExposureResponse {

    private final Market market;
    private final String ticker;
    private final BigDecimal marketValue;
    private final BigDecimal exposureRate;

    private HoldingExposureResponse(
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

    public static HoldingExposureResponse from(HoldingExposure exposure) {
        return new HoldingExposureResponse(
                exposure.getMarket(),
                exposure.getTicker(),
                exposure.getMarketValue().setScale(2, RoundingMode.HALF_UP),
                exposure.getExposureRate().setScale(2, RoundingMode.HALF_UP)
        );
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
