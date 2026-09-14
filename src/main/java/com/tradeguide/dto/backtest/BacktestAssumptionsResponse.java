package com.tradeguide.dto.backtest;

import com.tradeguide.domain.backtest.BacktestAssumptions;

import java.math.BigDecimal;

public class BacktestAssumptionsResponse {

    private final BigDecimal feeRate;
    private final BigDecimal slippageRate;

    public BacktestAssumptionsResponse(
            BigDecimal feeRate,
            BigDecimal slippageRate
    ) {
        this.feeRate = feeRate;
        this.slippageRate = slippageRate;
    }

    public static BacktestAssumptionsResponse from(BacktestAssumptions assumptions) {
        return new BacktestAssumptionsResponse(
                assumptions.getFeeRate(),
                assumptions.getSlippageRate()
        );
    }

    public BigDecimal getFeeRate() {
        return feeRate;
    }

    public BigDecimal getSlippageRate() {
        return slippageRate;
    }
}
