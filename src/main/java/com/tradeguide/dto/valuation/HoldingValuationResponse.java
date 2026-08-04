package com.tradeguide.dto.valuation;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.HoldingValuation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class HoldingValuationResponse {
    private final Market market;
    private final String ticker;
    private final BigDecimal quantity;
    private final BigDecimal averagePurchasePrice;
    private final BigDecimal currentPrice;
    private final BigDecimal purchaseAmount;
    private final BigDecimal marketValue;
    private final BigDecimal unrealizedProfitLoss;
    private final BigDecimal returnRate;

    public HoldingValuationResponse(
            Market market,
            String ticker,
            BigDecimal quantity,
            BigDecimal averagePurchasePrice,
            BigDecimal currentPrice,
            BigDecimal purchaseAmount,
            BigDecimal marketValue,
            BigDecimal unrealizedProfitLoss,
            BigDecimal returnRate
    ) {
        this.market = market;
        this.ticker = ticker;
        this.quantity = quantity;
        this.averagePurchasePrice = averagePurchasePrice;
        this.currentPrice = currentPrice;
        this.purchaseAmount = purchaseAmount;
        this.marketValue = marketValue;
        this.unrealizedProfitLoss = unrealizedProfitLoss;
        this.returnRate = returnRate;
    }
    
    public static HoldingValuationResponse from(HoldingValuation valuation) {
        return new HoldingValuationResponse(
                valuation.getMarket(),
                valuation.getTicker(),
                valuation.getQuantity(),
                valuation.getAveragePurchasePrice().setScale(2, RoundingMode.HALF_UP),
                valuation.getCurrentPrice().setScale(2, RoundingMode.HALF_UP),
                valuation.getPurchaseAmount().setScale(2, RoundingMode.HALF_UP),
                valuation.getMarketValue().setScale(2, RoundingMode.HALF_UP),
                valuation.getUnrealizedProfitLoss().setScale(2, RoundingMode.HALF_UP),
                valuation.getReturnRate().setScale(2, RoundingMode.HALF_UP)
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

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getPurchaseAmount() {
        return purchaseAmount;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public BigDecimal getUnrealizedProfitLoss() {
        return unrealizedProfitLoss;
    }

    public BigDecimal getReturnRate() {
        return returnRate;
    }
}
