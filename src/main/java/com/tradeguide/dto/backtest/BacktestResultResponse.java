package com.tradeguide.dto.backtest;

import com.tradeguide.domain.backtest.BacktestResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class BacktestResultResponse {

    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final BigDecimal startingPortfolioValue;
    private final BigDecimal endingPortfolioValue;
    private final BigDecimal cumulativeReturnRate;
    private final BigDecimal maxDrawdownRate;
    private final int tradeCount;
    private final List<BacktestTradeEventResponse> trades;
    private final BacktestAssumptionsResponse assumptions;

    public BacktestResultResponse(
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal startingPortfolioValue,
            BigDecimal endingPortfolioValue,
            BigDecimal cumulativeReturnRate,
            BigDecimal maxDrawdownRate,
            int tradeCount,
            List<BacktestTradeEventResponse> trades,
            BacktestAssumptionsResponse assumptions
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

    public static BacktestResultResponse from(BacktestResult result) {
        return new BacktestResultResponse(
                result.getPeriodStart(),
                result.getPeriodEnd(),
                result.getStartingPortfolioValue(),
                result.getEndingPortfolioValue(),
                result.getCumulativeReturnRate(),
                result.getMaxDrawdownRate(),
                result.getTradeCount(),
                result.getTrades().stream()
                        .map(BacktestTradeEventResponse::from)
                        .toList(),
                BacktestAssumptionsResponse.from(result.getAssumptions())
        );
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

    public List<BacktestTradeEventResponse> getTrades() {
        return trades;
    }

    public BacktestAssumptionsResponse getAssumptions() {
        return assumptions;
    }
}
