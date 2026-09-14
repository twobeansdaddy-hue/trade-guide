package com.tradeguide.domain.strategy;

/**
 * 포트폴리오의 매매 원장 기반 보유 종목이 0건이라 가이드 목록 자체가 비어 있는 이유를
 * 구분한다. 전략 프로필 저장은 보유 수량을 만들지 않으므로, 이 사유는 항상 원장
 * ({@code TradeTransaction}) 상태만을 기준으로 판단한다.
 */
public enum EmptyHoldingsReason {

    /**
     * 원장에 매매 기록이 없고, 참고할 증권사 보유 종목 스냅샷도 없다. 매매 기록을 직접
     * 등록하거나 증권사 계좌를 연동해야 한다.
     */
    NO_BROKER_SNAPSHOT,

    /**
     * 증권사 보유 종목 스냅샷은 있지만 아직 원장에 반영되지 않았다. 개시 잔고 반영 또는
     * 보유 종목 반영을 진행해야 가이드가 계산된다.
     */
    BROKER_SNAPSHOT_NOT_REFLECTED
}
