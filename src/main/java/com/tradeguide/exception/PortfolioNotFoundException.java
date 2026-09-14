package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 증권사 연동 경로에서 요청한 포트폴리오가 없거나 이 회원 소유가 아닐 때 발생한다. 404로
 * 매핑된다.
 *
 * <p>이전에는 {@link IllegalArgumentException}으로 던져 400과 구분되지 않았다. 리소스 부재는
 * 잘못된 요청 형식과 다른 상황이므로 분리한다.
 */
public class PortfolioNotFoundException extends RuntimeException {

    public PortfolioNotFoundException(String message) {
        super(message);
    }

    public ApiErrorCode getCode() {
        return ApiErrorCode.PORTFOLIO_NOT_FOUND;
    }
}
