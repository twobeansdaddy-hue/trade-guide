package com.tradeguide.domain.holding;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

public class Holding {

    private final Market market;
    private final String ticker;
    private final BigDecimal quantity;
    private final BigDecimal averagePurchasePrice;

    public Holding(
            Market market,
            String ticker,
            BigDecimal quantity,
            BigDecimal averagePurchasePrice
    ) {
        this.market = market;
        this.ticker = ticker;
        this.quantity = quantity;
        this.averagePurchasePrice = averagePurchasePrice;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAveragePurchasePrice() {
        return averagePurchasePrice;
    }
}
