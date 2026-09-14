package com.tradeguide.dto.market;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.trade.Market;

import java.util.List;

public class MarketDataProviderResponse {

    private final MarketDataProvider provider;
    private final String displayName;
    private final boolean requiresBrokerConnection;
    private final boolean selectable;
    private final boolean configured;
    private final List<Market> supportedMarkets;

    public MarketDataProviderResponse(MarketDataProvider provider, boolean configured) {
        this.provider = provider;
        this.displayName = provider.getDisplayName();
        this.requiresBrokerConnection = provider.requiresBrokerConnection();
        this.selectable = provider.isSelectable();
        this.configured = configured;
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

    /**
     * 이 제공자를 사용하기 위한 서버 측 설정이 준비됐는지 여부다. 비밀값은 포함하지 않는다.
     * 회원별 증권사 연결이 추가로 필요한지는 {@code requiresBrokerConnection}이 표현한다.
     */
    public boolean isConfigured() {
        return configured;
    }

    public List<Market> getSupportedMarkets() {
        return supportedMarkets;
    }
}
