package com.tradeguide.domain.broker;

/**
 * 주문이 반영 후보에서 빠진 구체적 사유다. 화면이 사유별로 다른 안내를 하기 위한 값이며,
 * 증권사 원문 오류 메시지를 담지 않는다.
 */
public enum BrokerOrderSkipReason {
    /** 체결 수량이 0이다. */
    NOT_FILLED,
    /** 부분 체결이라 종료를 단정할 수 없다. */
    PARTIAL_FILL_PENDING,
    /** 취소·정정 거부로 생긴 제어 기록이다. */
    CONTROL_RECORD,
    /** 체결분은 있는데 최종 체결 시각이 없다. 결제일로 대신 채우지 않는다. */
    MISSING_EXECUTION_TIME,
    /** 체결분은 있는데 평균 체결가가 없다. 가격을 추정하지 않는다. */
    MISSING_AVERAGE_PRICE,
    /** 제공자가 이 클라이언트가 모르는 상태 값을 보냈다. */
    UNKNOWN_STATUS,
    /** 이미 매매 원장에 반영된 주문이다. */
    ALREADY_IMPORTED,
    /** 수동 입력 매매와 같은 거래로 의심된다. */
    MANUAL_OVERLAP,
    /** 이미 아는 주문과 내용 지문이 같은데 주문 식별자만 다르다. */
    DUPLICATE_FINGERPRINT
}
