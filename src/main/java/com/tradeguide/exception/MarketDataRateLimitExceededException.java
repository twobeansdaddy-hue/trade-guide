package com.tradeguide.exception;

public class MarketDataRateLimitExceededException
        extends RuntimeException {

    public MarketDataRateLimitExceededException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}