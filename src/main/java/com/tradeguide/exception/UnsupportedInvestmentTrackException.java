package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 실행 가능한 {@code TradingStrategy} 구현이 없는 투자 트랙을 선택하거나 재정의로
 * 저장하려 할 때 발생한다. 422로 매핑된다.
 */
public class UnsupportedInvestmentTrackException extends RuntimeException {

    public UnsupportedInvestmentTrackException(String message) {
        super(message);
    }

    public ApiErrorCode getCode() {
        return ApiErrorCode.UNSUPPORTED_INVESTMENT_TRACK;
    }
}
