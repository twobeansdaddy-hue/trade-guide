package com.tradeguide.domain.strategy;

/**
 * {@link TradePlanPreviewStatus#NOT_READY} 상태의 사유. 화면이 문구가 아니라 코드로
 * 분기할 수 있게 한다.
 */
public enum TradePlanPreviewNotReadyReason {

    /** 포트폴리오에 {@code PortfolioRiskPolicy}가 설정되지 않았다. */
    MISSING_RISK_POLICY,

    /** 위험 한도 정책은 있지만 사용자 설정 손절 기준 비율이 없어 손절가를 계산할 수 없다. */
    MISSING_STOP_LOSS
}
