package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.UnavailableAsset;
import com.tradeguide.domain.trade.Market;

public class UnavailableAssetResponse {

    private final Market market;
    private final String ticker;
    private final String message;

    public UnavailableAssetResponse(
            Market market,
            String ticker,
            String message
    ) {
        this.market = market;
        this.ticker = ticker;
        this.message = message;
    }

    public static UnavailableAssetResponse from(UnavailableAsset unavailableAsset) {
        return new UnavailableAssetResponse(
                unavailableAsset.getMarket(),
                unavailableAsset.getTicker(),
                unavailableAsset.getMessage()
        );
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public String getMessage() {
        return message;
    }
}
