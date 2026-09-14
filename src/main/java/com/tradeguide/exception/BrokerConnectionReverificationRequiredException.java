package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 포트폴리오에 연결된 증권사 연결이 {@code CONNECTED} 상태가 아니라서 조회·저장을 진행할 수
 * 없을 때 발생한다. 409로 매핑된다.
 *
 * <p>연결을 다시 검증하면 해결될 수 있는 상태 충돌이라 리소스 부재(404)가 아니라 충돌(409)로
 * 다룬다. 이전에는 {@link IllegalArgumentException}으로 던져 400과 구분되지 않았다.
 */
public class BrokerConnectionReverificationRequiredException extends RuntimeException {

    public BrokerConnectionReverificationRequiredException(String message) {
        super(message);
    }

    public ApiErrorCode getCode() {
        return ApiErrorCode.BROKER_CONNECTION_REVERIFICATION_REQUIRED;
    }
}
