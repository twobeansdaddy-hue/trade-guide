package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 삭제하거나 조회하려는 포트폴리오 후보 종목이 없을 때 발생한다. 404로 매핑된다.
 */
public class PortfolioCandidateAssetNotFoundException extends RuntimeException {

    public PortfolioCandidateAssetNotFoundException(String message) {
        super(message);
    }

    public ApiErrorCode getCode() {
        return ApiErrorCode.PORTFOLIO_CANDIDATE_ASSET_NOT_FOUND;
    }
}
