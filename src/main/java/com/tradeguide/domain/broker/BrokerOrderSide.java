package com.tradeguide.domain.broker;

/**
 * 증권사가 보고한 주문 방향이다.
 * 제공자 코드 체계와 분리된 중립 값이며, 매매 원장의 {@link com.tradeguide.domain.trade.TradeType}과는
 * 별개다. 원장 반영 단계에서만 두 값을 잇는다.
 */
public enum BrokerOrderSide {
    BUY,
    SELL
}
