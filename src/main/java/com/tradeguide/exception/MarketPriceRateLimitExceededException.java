package com.tradeguide.exception;

public class MarketPriceRateLimitExceededException
        extends RuntimeException {

    public MarketPriceRateLimitExceededException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}