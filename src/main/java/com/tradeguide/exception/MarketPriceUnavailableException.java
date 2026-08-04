package com.tradeguide.exception;

public class MarketPriceUnavailableException extends RuntimeException {
    public MarketPriceUnavailableException(String message) {
        super(message);
    }

    public MarketPriceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
