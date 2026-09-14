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
    /**
     * {@code selectable=true}이지만, 실제 선택은 포트폴리오에 연결된 <b>검증된</b> 토스증권
     * 연결이 있을 때만 허용된다. 이 런타임 조건은 이 enum이 아니라
     * {@code PortfolioService.updateMarketDataPreference}와
     * {@code MarketPriceProviderRegistry}가 판정한다({@code requiresBrokerConnection=true}가
     * 그 전제 조건이 있다는 사실만 카탈로그 응답으로 알린다).
     */
    TOSS_SECURITIES(
            "토스증권",
            true,
            true,
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
