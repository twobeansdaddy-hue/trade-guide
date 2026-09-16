package com.tradeguide.domain.trade;

public enum Market {
    US,
    KR;

    public Currency getCurrency() {
        return switch (this) {
            case US -> Currency.USD;
            case KR -> Currency.KRW;
        };
    }
}
