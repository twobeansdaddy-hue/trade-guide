package com.tradeguide.exception;

/**
 * 증권사 개시 잔고로 생성된 매매 기록을 일반 매매 삭제 API로 지우려 할 때 발생한다.
 * 이런 행은 전용 개시 잔고 취소 API로만 되돌릴 수 있다.
 */
public class TradeTransactionProtectedException extends RuntimeException {
    public TradeTransactionProtectedException(String message) {
        super(message);
    }
}
