package com.tradeguide.exception;

/**
 * 증권사 개시 잔고 승인 대상이 {@code ONLY_IN_BROKER}가 아니거나, 비활성 자산
 * 카탈로그 종목이라 반영할 수 없을 때 발생한다.
 */
public class BrokerHoldingImportConflictException extends RuntimeException {
    public BrokerHoldingImportConflictException(String message) {
        super(message);
    }
}
