package com.tradeguide.exception;

/**
 * 증권사 잔고 조정 승인 대상 스냅샷 항목의 비교 결과가 {@code QUANTITY_MISMATCH}가
 * 아닐 때 발생한다.
 */
public class BrokerHoldingAdjustmentConflictException extends RuntimeException {
    public BrokerHoldingAdjustmentConflictException(String message) {
        super(message);
    }
}
