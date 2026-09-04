package com.tradeguide.domain.market;

import com.tradeguide.domain.trade.Market;

import java.util.Set;

public enum MarketDataProvider {
    TWELVE_DATA(
            "Twelve Data",
            false,
            true,
            Set.of(Market.US)
    ),
    TOSS_SECURITIES(
            "토스증권",
            true,
            false,
            Set.of(Market.US, Market.KR)
    ),
    YAHOO_FINANCE(
            "Yahoo Finance",
            false,
            false,
            Set.of(Market.US, Market.KR)
    );

    private final String displayName;
    private final boolean requiresBrokerConnection;
    private final boolean selectable;
    private final Set<Market> supportedMarkets;

    MarketDataProvider(
            String displayName,
            boolean requiresBrokerConnection,
            boolean selectable,
            Set<Market> supportedMarkets
    ) {
        this.displayName = displayName;
        this.requiresBrokerConnection = requiresBrokerConnection;
        this.selectable = selectable;
        this.supportedMarkets = supportedMarkets;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresBrokerConnection() {
        return requiresBrokerConnection;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public Set<Market> getSupportedMarkets() {
        return supportedMarkets;
    }
}
