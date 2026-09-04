package com.tradeguide.dto.market;

import com.tradeguide.domain.market.PortfolioMarketDataPreference;
import com.tradeguide.domain.market.MarketDataProvider;

public class PortfolioMarketDataPreferenceResponse {

    private final MarketDataProvider priceProvider;
    private final MarketDataProvider candleProvider;
    private final MarketDataProvider assetReferenceProvider;

    private PortfolioMarketDataPreferenceResponse(
            MarketDataProvider priceProvider,
            MarketDataProvider candleProvider,
            MarketDataProvider assetReferenceProvider
    ) {
        this.priceProvider = priceProvider;
        this.candleProvider = candleProvider;
        this.assetReferenceProvider = assetReferenceProvider;
    }

    public static PortfolioMarketDataPreferenceResponse from(
            PortfolioMarketDataPreference preference
    ) {
        return new PortfolioMarketDataPreferenceResponse(
                preference.getPriceProvider(),
                preference.getCandleProvider(),
                preference.getAssetReferenceProvider()
        );
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
