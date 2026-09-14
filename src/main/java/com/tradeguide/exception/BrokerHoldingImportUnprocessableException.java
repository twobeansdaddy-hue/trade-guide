package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 요청 형식은 옳지만 개시 잔고로 반영할 수 없는 종목이라는 판정이다. 422로 매핑된다.
 *
 * <p>{@link #getCode()}는 선택 값이다. 클라이언트가 문구가 아니라 코드로 분기해야 하는
 * 사유에만 부여하고, 코드가 없는 기존 오류의 응답 형태는 그대로 둔다.
 */
public class BrokerHoldingImportUnprocessableException extends RuntimeException {

    private final ApiErrorCode code;

    public BrokerHoldingImportUnprocessableException(String message) {
        this(message, null);
    }

    public BrokerHoldingImportUnprocessableException(String message, ApiErrorCode code) {
        super(message);
        this.code = code;
    }

    public ApiErrorCode getCode() {
        return code;
    }
}
