package com.tradeguide.domain.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 특정 리밸런싱 시점에 한 종목의 형성기간(52주 중 최근 4주 제외, 48주) 수익률과
 * 유니버스 내 순위(1이 최상위)를 담는다.
 */
public class MomentumFormationScore {

    private final String ticker;
    private final LocalDate rebalanceDate;
    private final BigDecimal formationReturnRate;
    private final int rank;

    public MomentumFormationScore(
            String ticker,
            LocalDate rebalanceDate,
            BigDecimal formationReturnRate,
            int rank
    ) {
        this.ticker = ticker;
        this.rebalanceDate = rebalanceDate;
        this.formationReturnRate = formationReturnRate;
        this.rank = rank;
    }

    public String getTicker() {
        return ticker;
    }

    public LocalDate getRebalanceDate() {
        return rebalanceDate;
    }

    public BigDecimal getFormationReturnRate() {
        return formationReturnRate;
    }

    public int getRank() {
        return rank;
    }
}
