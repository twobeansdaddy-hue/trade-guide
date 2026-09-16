package com.tradeguide.domain.strategy;

/**
 * 검토용 매수 계획에 적용된 제약을 코드로 표시한다. 화면이 문구 대신 이 값으로 배지나
 * 경고를 분기할 수 있게 한다.
 */
public enum TradePlanPreviewConstraint {

    /**
     * 증권사 가용 현금 잔고가 아직 신뢰할 수 있는 공급자 간 공통 계약이 아니어서 이 금액이
     * 실제로 매수 가능한지 확인하지 않았다. 가용 현금을 임의로 가정하지 않는다.
     */
    AVAILABLE_CASH_NOT_SYNCED,

    /**
     * 위험 금액 기준 수량이 종목당 최대 노출 비율의 남은 한도로 축소됐다.
     */
    SINGLE_ASSET_EXPOSURE_CAP_APPLIED
}
