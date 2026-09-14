package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 이미 후보로 등록된 종목을 같은 포트폴리오에 다시 등록하려 할 때 발생한다. 409로 매핑된다.
 */
public class PortfolioCandidateAssetAlreadyExistsException extends RuntimeException {

    public PortfolioCandidateAssetAlreadyExistsException(String message) {
        super(message);
    }

    public ApiErrorCode getCode() {
        return ApiErrorCode.PORTFOLIO_CANDIDATE_ASSET_ALREADY_EXISTS;
    }
}
