package com.tradeguide.domain.market;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class PortfolioMarketDataPreference {

    @Enumerated(EnumType.STRING)
    private MarketDataProvider priceProvider;

    @Enumerated(EnumType.STRING)
    private MarketDataProvider candleProvider;

    @Enumerated(EnumType.STRING)
    private MarketDataProvider assetReferenceProvider;

    protected PortfolioMarketDataPreference() {
    }

    private PortfolioMarketDataPreference(
            MarketDataProvider priceProvider,
            MarketDataProvider candleProvider,
            MarketDataProvider assetReferenceProvider
    ) {
        if (priceProvider == null || candleProvider == null || assetReferenceProvider == null) {
            throw new IllegalArgumentException("시장 데이터 제공자는 필수입니다.");
        }

        this.priceProvider = priceProvider;
        this.candleProvider = candleProvider;
        this.assetReferenceProvider = assetReferenceProvider;
    }

    public static PortfolioMarketDataPreference unified(MarketDataProvider provider) {
        return new PortfolioMarketDataPreference(provider, provider, provider);
    }

    public MarketDataProvider getPriceProvider() {
        return priceProvider;
    }

    public MarketDataProvider getCandleProvider() {
        return candleProvider;
    }

    public MarketDataProvider getAssetReferenceProvider() {
        return assetReferenceProvider;
    }
}
