package com.tradeguide.domain.asset;

import com.tradeguide.domain.trade.Market;

public record AssetSearchResult(
        Market market,
        String ticker,
        String displayName
) {
}
