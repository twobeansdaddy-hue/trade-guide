package com.tradeguide.dto.market;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.trade.Market;

import java.util.List;

public class MarketDataProviderResponse {

    private final MarketDataProvider provider;
    private final String displayName;
    private final boolean requiresBrokerConnection;
    private final boolean selectable;
    private final List<Market> supportedMarkets;

    public MarketDataProviderResponse(MarketDataProvider provider) {
        this.provider = provider;
        this.displayName = provider.getDisplayName();
        this.requiresBrokerConnection = provider.requiresBrokerConnection();
        this.selectable = provider.isSelectable();
        this.supportedMarkets = provider.getSupportedMarkets().stream().sorted().toList();
    }

    public MarketDataProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isRequiresBrokerConnection() {
        return requiresBrokerConnection;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public List<Market> getSupportedMarkets() {
        return supportedMarkets;
    }
}
