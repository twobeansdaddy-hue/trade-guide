package com.tradeguide.domain.backtest;

import java.time.LocalDate;

/**
 * 종목 하나가 횡단면 모멘텀 순위 또는 리밸런싱에서 제외된 기록.
 * {@code rebalanceDate}가 {@code null}이면 전체 실행 기간에 걸친 전역 제외
 * (예: 중복 거래일)이고, 값이 있으면 해당 리밸런싱 시점에서만의 제외
 * (예: 특정 시점의 이력 부족)다.
 */
public class MomentumDataQualityExclusion {

    private final String ticker;
    private final LocalDate rebalanceDate;
    private final MomentumDataQualityReason reason;
    private final String detail;

    public MomentumDataQualityExclusion(
            String ticker,
            LocalDate rebalanceDate,
            MomentumDataQualityReason reason,
            String detail
    ) {
        this.ticker = ticker;
        this.rebalanceDate = rebalanceDate;
        this.reason = reason;
        this.detail = detail;
    }

    public String getTicker() {
        return ticker;
    }

    public LocalDate getRebalanceDate() {
        return rebalanceDate;
    }

    public MomentumDataQualityReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }
}
