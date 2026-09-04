package com.tradeguide.exception;

public class BrokerConnectionUnavailableException extends RuntimeException {
    public BrokerConnectionUnavailableException(String message) {
        super(message);
    }
}
