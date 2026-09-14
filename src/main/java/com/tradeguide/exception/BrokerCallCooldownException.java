package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 같은 포트폴리오의 같은 증권사 조회 동작을 직전 호출 이후 최소 대기 시간(쿨다운)이 지나기
 * 전에 다시 요청했을 때 발생한다. 429로 매핑된다.
 *
 * <p>이 쿨다운은 증권사의 실제 호출 빈도 제한을 안다고 주장하지 않는다. 실수로 인한 더블클릭과
 * 같은 화면의 경쟁 재시도를 줄이려는 우리 쪽 보수적 기본값이다.
 */
public class BrokerCallCooldownException extends RuntimeException {

    private final long retryAfterSeconds;

    public BrokerCallCooldownException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public ApiErrorCode getCode() {
        return ApiErrorCode.BROKER_CALL_COOLDOWN;
    }
}
