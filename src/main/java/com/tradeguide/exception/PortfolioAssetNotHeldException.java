package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 현재 보유하지 않은 종목에 포트폴리오 전략 프로필 재정의를 설정하려 할 때 발생한다. 422로
 * 매핑된다.
 */
public class PortfolioAssetNotHeldException extends RuntimeException {

    public PortfolioAssetNotHeldException(String message) {
        super(message);
    }

    public ApiErrorCode getCode() {
        return ApiErrorCode.PORTFOLIO_ASSET_NOT_HELD;
    }
}
