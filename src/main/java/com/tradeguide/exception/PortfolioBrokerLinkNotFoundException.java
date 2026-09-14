package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 포트폴리오에 연결된 증권사 계좌 링크가 없을 때 발생한다. 404로 매핑된다.
 *
 * <p>이전에는 {@link IllegalArgumentException}으로 던져 400과 구분되지 않았다. 링크라는
 * 리소스가 없다는 판정이지 요청 형식 오류가 아니다.
 */
public class PortfolioBrokerLinkNotFoundException extends RuntimeException {

    public PortfolioBrokerLinkNotFoundException(String message) {
        super(message);
    }

    public ApiErrorCode getCode() {
        return ApiErrorCode.PORTFOLIO_BROKER_LINK_NOT_FOUND;
    }
}
