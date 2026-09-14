package com.tradeguide.domain.trade;

/**
 * 매매 기록이 생성된 출처다. 사용자가 직접 입력한 기록과 증권사 연동(개시 잔고·주문 이력)으로
 * 생성된 기록을 구분해, 후자를 전용 취소 API로만 되돌릴 수 있게 한다.
 */
public enum TradeTransactionSource {
    MANUAL,
    BROKER_OPENING_BALANCE,
    /** 증권사 주문 이력 가져오기 승인으로 생성된 기록이다. 전용 취소 API로만 되돌릴 수 있다. */
    BROKER_ORDER_HISTORY,
    /**
     * 이미 원장에 있는 종목의 증권사 잔고 조정(수량 불일치 해소) 승인으로 생성된 기록이다.
     * 전용 취소 API로만 되돌릴 수 있다.
     */
    BROKER_HOLDING_ADJUSTMENT
}
