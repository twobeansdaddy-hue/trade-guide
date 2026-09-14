package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 삭제하거나 조회하려는 포트폴리오 전략 프로필 재정의가 없을 때 발생한다. 404로 매핑된다.
 */
public class PortfolioAssetStrategyProfileNotFoundException extends RuntimeException {

    public PortfolioAssetStrategyProfileNotFoundException(String message) {
        super(message);
    }

    public ApiErrorCode getCode() {
        return ApiErrorCode.PORTFOLIO_ASSET_STRATEGY_PROFILE_NOT_FOUND;
    }
}
