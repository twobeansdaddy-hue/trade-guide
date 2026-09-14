package com.tradeguide.domain.broker;

/**
 * 정합성 점검 실행 한 건의 전체 결과다. 종목별 줄의 {@link BrokerHoldingComparison}을
 * 집계한 값이며, 이 값 자체는 매매 원장을 바꾸지 않는다.
 */
public enum BrokerReconciliationOverallStatus {
    /** 모든 종목의 비교 결과가 일치했다. */
    MATCHED,
    /** 하나 이상의 종목에서 차이가 발견됐다. */
    DIFFERENCES_FOUND
}
