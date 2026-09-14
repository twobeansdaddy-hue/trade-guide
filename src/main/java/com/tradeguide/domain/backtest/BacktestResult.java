package com.tradeguide.domain.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class BacktestResult {

    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final BigDecimal startingPortfolioValue;
    private final BigDecimal endingPortfolioValue;
    private final BigDecimal cumulativeReturnRate;
    private final BigDecimal maxDrawdownRate;
    private final int tradeCount;
    private final List<BacktestTradeEvent> trades;
    private final BacktestAssumptions assumptions;

    public BacktestResult(
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal startingPortfolioValue,
            BigDecimal endingPortfolioValue,
            BigDecimal cumulativeReturnRate,
            BigDecimal maxDrawdownRate,
            int tradeCount,
            List<BacktestTradeEvent> trades,
            BacktestAssumptions assumptions
    ) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.startingPortfolioValue = startingPortfolioValue;
        this.endingPortfolioValue = endingPortfolioValue;
        this.cumulativeReturnRate = cumulativeReturnRate;
        this.maxDrawdownRate = maxDrawdownRate;
        this.tradeCount = tradeCount;
        this.trades = trades;
        this.assumptions = assumptions;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getStartingPortfolioValue() {
        return startingPortfolioValue;
    }

    public BigDecimal getEndingPortfolioValue() {
        return endingPortfolioValue;
    }

    public BigDecimal getCumulativeReturnRate() {
        return cumulativeReturnRate;
    }

    public BigDecimal getMaxDrawdownRate() {
        return maxDrawdownRate;
    }

    public int getTradeCount() {
        return tradeCount;
    }

    public List<BacktestTradeEvent> getTrades() {
        return trades;
    }

    public BacktestAssumptions getAssumptions() {
        return assumptions;
    }
}
