package com.tradeguide.dto.holding;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

public class HoldingResponse {
    private final Market market;
    private final String ticker;
    private final BigDecimal quantity;
    private final BigDecimal averagePurchasePrice;

    private HoldingResponse(Market market, String ticker, BigDecimal quantity, BigDecimal averagePurchasePrice) {
        this.market = market;
        this.ticker = ticker;
        this.quantity = quantity;
        this.averagePurchasePrice = averagePurchasePrice;
    }

    public static HoldingResponse from(Holding holding) {
        return new HoldingResponse(
                holding.getMarket(),
                holding.getTicker(),
                holding.getQuantity(),
                holding.getAveragePurchasePrice()
        );
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
