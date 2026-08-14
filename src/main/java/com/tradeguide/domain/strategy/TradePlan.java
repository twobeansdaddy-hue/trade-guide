package com.tradeguide.domain.strategy;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeType;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TradePlan {

    private final Market market;
    private final String ticker;
    private final TradeType tradeType;
    private final BigDecimal quantityRatio;
    private final BigDecimal limitPrice;
    private final BigDecimal stopLossPrice;
    private final LocalDate validUntil;
    private final String reason;
    private final StrategyMetadata strategyMetadata;

    public TradePlan(
            Market market,
            String ticker,
            TradeType tradeType,
            BigDecimal quantityRatio,
            BigDecimal limitPrice,
            BigDecimal stopLossPrice,
            LocalDate validUntil,
            String reason,
            StrategyMetadata strategyMetadata
    ) {
        if (market == null) {
            throw new IllegalArgumentException("시장은 필수입니다.");
        }

        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("티커는 비어 있을 수 없습니다.");
        }

        if (tradeType == null) {
            throw new IllegalArgumentException("주문 유형은 필수입니다.");
        }

        if (validUntil == null) {
            throw new IllegalArgumentException("주문 유효 기간은 필수입니다.");
        }

        if (strategyMetadata == null) {
            throw new IllegalArgumentException("전략 메타데이터는 필수입니다.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("주문 근거는 비어 있을 수 없습니다.");
        }

        if (quantityRatio == null
                || quantityRatio.compareTo(BigDecimal.ZERO) <= 0
                || quantityRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("주문 비율은 0보다 크고 1 이하여야 합니다.");
        }

        if (limitPrice == null
                || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("지정가는 0보다 커야 합니다.");
        }

        if (stopLossPrice == null
                || stopLossPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("손절가는 0보다 커야 합니다.");
        }

        this.market = market;
        this.ticker = ticker;
        this.tradeType = tradeType;
        this.quantityRatio = quantityRatio;
        this.limitPrice = limitPrice;
        this.stopLossPrice = stopLossPrice;
        this.validUntil = validUntil;
        this.reason = reason;
        this.strategyMetadata = strategyMetadata;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public BigDecimal getQuantityRatio() {
        return quantityRatio;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public BigDecimal getStopLossPrice() {
        return stopLossPrice;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public String getReason() {
        return reason;
    }

    public StrategyMetadata getStrategyMetadata() {
        return strategyMetadata;
    }
}
