package com.tradeguide.exception;

public class MarketDataRateLimitExceededException
        extends RuntimeException {

    private final Long retryAfterSeconds;

    public MarketDataRateLimitExceededException(
            String message,
            Throwable cause
    ) {
        this(message, cause, null);
    }

    public MarketDataRateLimitExceededException(
            String message,
            Throwable cause,
            Long retryAfterSeconds
    ) {
        super(message, cause);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** 알 수 있는 경우에만 값을 담는다. 클라이언트는 이 값이 없어도 재시도할 수 있어야 한다. */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}