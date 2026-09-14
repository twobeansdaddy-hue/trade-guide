package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 주문 항목 재판정을 이 상태로는 기록할 수 없을 때 발생한다. 의심 항목이 아니거나
 * ({@link ApiErrorCode#ORDER_IMPORT_ITEM_NOT_OVERRIDABLE}), 이미 다른 결정으로 재판정된 항목인
 * 경우다({@link ApiErrorCode#ORDER_IMPORT_OVERRIDE_CONFLICT}). HTTP 상태는 409로 고정이다.
 */
public class BrokerOrderOverrideConflictException extends RuntimeException {

    private final ApiErrorCode code;

    public BrokerOrderOverrideConflictException(String message, ApiErrorCode code) {
        super(message);
        this.code = code;
    }

    public ApiErrorCode getCode() {
        return code;
    }
}
