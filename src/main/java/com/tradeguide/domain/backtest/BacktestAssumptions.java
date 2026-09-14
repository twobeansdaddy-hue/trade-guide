package com.tradeguide.domain.backtest;

import java.math.BigDecimal;

// 초기 백테스트 가정. 수수료와 슬리피지는 검증 전까지 0으로 고정하며,
// 실제 체결 비용을 반영하려면 이 값을 먼저 리서치·정책 검토를 거쳐 바꿔야 한다.
public class BacktestAssumptions {

    private final BigDecimal feeRate;
    private final BigDecimal slippageRate;

    public BacktestAssumptions(
            BigDecimal feeRate,
            BigDecimal slippageRate
    ) {
        this.feeRate = feeRate;
        this.slippageRate = slippageRate;
    }

    public static BacktestAssumptions zeroCost() {
        return new BacktestAssumptions(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public BigDecimal getFeeRate() {
        return feeRate;
    }

    public BigDecimal getSlippageRate() {
        return slippageRate;
    }
}
