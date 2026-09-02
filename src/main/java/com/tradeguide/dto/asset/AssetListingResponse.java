package com.tradeguide.dto.asset;

import com.tradeguide.domain.asset.AssetSearchResult;
import com.tradeguide.domain.trade.Market;

public record AssetListingResponse(
        Market market,
        String ticker,
        String displayName
) {
    public static AssetListingResponse from(AssetSearchResult assetListing) {
        return new AssetListingResponse(
                assetListing.market(),
                assetListing.ticker(),
                assetListing.displayName()
        );
    }
}
