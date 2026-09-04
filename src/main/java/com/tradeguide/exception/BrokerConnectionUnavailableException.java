package com.tradeguide.exception;

public class BrokerConnectionUnavailableException extends RuntimeException {
    public BrokerConnectionUnavailableException(String message) {
        super(message);
    }

    public BrokerConnectionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
