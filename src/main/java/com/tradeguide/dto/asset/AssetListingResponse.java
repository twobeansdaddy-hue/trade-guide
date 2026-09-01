package com.tradeguide.dto.asset;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.trade.Market;

public record AssetListingResponse(
        Long id,
        Market market,
        String ticker,
        String displayName
) {
    public static AssetListingResponse from(AssetListing assetListing) {
        return new AssetListingResponse(
                assetListing.getId(),
                assetListing.getMarket(),
                assetListing.getTicker(),
                assetListing.getDisplayName()
        );
    }
}
