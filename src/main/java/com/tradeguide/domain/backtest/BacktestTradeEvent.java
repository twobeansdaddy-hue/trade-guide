package com.tradeguide.domain.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BacktestTradeEvent {

    private final LocalDate tradingDate;
    private final BacktestTradeType type;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final BigDecimal cashAfter;
    private final BigDecimal sharesAfter;
    private final BigDecimal portfolioValueAfter;

    public BacktestTradeEvent(
            LocalDate tradingDate,
            BacktestTradeType type,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal cashAfter,
            BigDecimal sharesAfter,
            BigDecimal portfolioValueAfter
    ) {
        this.tradingDate = tradingDate;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.cashAfter = cashAfter;
        this.sharesAfter = sharesAfter;
        this.portfolioValueAfter = portfolioValueAfter;
    }

    public LocalDate getTradingDate() {
        return tradingDate;
    }

    public BacktestTradeType getType() {
        return type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getCashAfter() {
        return cashAfter;
    }

    public BigDecimal getSharesAfter() {
        return sharesAfter;
    }

    public BigDecimal getPortfolioValueAfter() {
        return portfolioValueAfter;
    }
}
