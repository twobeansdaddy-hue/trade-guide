package com.tradeguide.domain.broker;

/**
 * 증권사 보유 종목과 Trade Guide 수동 매매 기록에서 계산한 보유 종목의 차이다.
 * 사용자가 명시적으로 가져오기를 결정하기 전에 검토할 정보이며,
 * 이 값만으로 매매 기록을 생성하거나 수정하지 않는다.
 */
public enum BrokerHoldingComparison {
    MATCHED,
    QUANTITY_MISMATCH,
    ONLY_IN_BROKER,
    ONLY_IN_TRADE_GUIDE
}
