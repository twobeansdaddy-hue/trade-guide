package com.tradeguide.domain.valuation;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.time.Instant;

public class HoldingValuation {
    private final Market market;
    private final String ticker;
    private final BigDecimal quantity;
    private final BigDecimal averagePurchasePrice;
    private final BigDecimal currentPrice;
    private final BigDecimal purchaseAmount;
    private final BigDecimal marketValue;
    private final BigDecimal unrealizedProfitLoss;
    private final BigDecimal returnRate;
    private final Instant priceAsOf;

    public HoldingValuation(
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
        this(
                market,
                ticker,
                quantity,
                averagePurchasePrice,
                currentPrice,
                purchaseAmount,
                marketValue,
                unrealizedProfitLoss,
                returnRate,
                null
        );
    }

    /**
     * {@code priceAsOf}는 {@code currentPrice}를 조회한 시점이다. 캐시된 값을
     * 그대로 재사용한 경우 실제 조회 시점보다 과거일 수 있으므로, 클라이언트가
     * 표시된 가격의 신선도를 판단할 수 있도록 그대로 노출한다.
     */
    public HoldingValuation(
            Market market,
            String ticker,
            BigDecimal quantity,
            BigDecimal averagePurchasePrice,
            BigDecimal currentPrice,
            BigDecimal purchaseAmount,
            BigDecimal marketValue,
            BigDecimal unrealizedProfitLoss,
            BigDecimal returnRate,
            Instant priceAsOf
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
        this.priceAsOf = priceAsOf;
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

    public Instant getPriceAsOf() {
        return priceAsOf;
    }
}
