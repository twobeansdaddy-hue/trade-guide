package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 증권사 연결 관련 전제 조건이 없거나 외부 호출이 실패해 요청을 처리할 수 없다는 판정이다.
 * 503으로 매핑된다.
 *
 * <p>{@link #getCode()}는 클라이언트가 문구가 아니라 코드로 분기할 수 있게 하는 구분 값이다.
 * 기본값은 {@link ApiErrorCode#BROKER_CONNECTION_UNAVAILABLE}이며, 기능 관문에서 거부된
 * 경우에만 {@link ApiErrorCode#BROKER_CAPABILITY_UNAVAILABLE}로 좁힌다. 둘을 구분하는
 * 이유는 대응이 다르기 때문이다. 전자는 재시도나 재검증으로 풀릴 수 있고, 후자는 그 제공자에서
 * 아직 열리지 않은 기능이라 재시도가 의미 없다.
 */
public class BrokerConnectionUnavailableException extends RuntimeException {

    private final ApiErrorCode code;

    public BrokerConnectionUnavailableException(String message) {
        this(message, ApiErrorCode.BROKER_CONNECTION_UNAVAILABLE);
    }

    public BrokerConnectionUnavailableException(String message, ApiErrorCode code) {
        super(message);
        this.code = code;
    }

    public BrokerConnectionUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.code = ApiErrorCode.BROKER_CONNECTION_UNAVAILABLE;
    }

    public ApiErrorCode getCode() {
        return code;
    }
}
