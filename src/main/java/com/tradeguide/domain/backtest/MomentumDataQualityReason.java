package com.tradeguide.domain.backtest;

/**
 * 횡단면 모멘텀 검증에서 종목을 순위 계산 또는 리밸런싱에서
 * 제외해야 하는 데이터 품질 사유를 명시적으로 분류한다.
 * 이 이유 없이 종목을 조용히 무시하지 않는다.
 */
public enum MomentumDataQualityReason {
    /** 형성기간(52주) 계산에 필요한 이력이 해당 리밸런싱 시점까지 충분히 쌓이지 않음. */
    INSUFFICIENT_HISTORY,
    /** 종목의 캔들 이력에 동일한 거래일이 중복돼 어느 값이 유효한지 판정할 수 없음. */
    DUPLICATE_TRADING_DATE,
    /** 종목의 캔들 이력이 거래일 오름차순으로 정렬돼 있지 않음. */
    UNSORTED_HISTORY,
    /** 해당 리밸런싱 시점(거래일)에 종목의 캔들 데이터 자체가 없음. */
    MISSING_HISTORY
}
