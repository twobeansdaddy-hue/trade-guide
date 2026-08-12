package com.tradeguide.exception;

public class StaleMarketDataException extends MarketDataUnavailableException {

    public StaleMarketDataException(String message) {
        super(message);
    }
}
