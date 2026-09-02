package com.tradeguide.service.asset;

import com.tradeguide.domain.asset.AssetSearchResult;
import com.tradeguide.domain.trade.Market;

import java.util.List;

public interface AssetSearchProvider {
    List<AssetSearchResult> search(Market market, String query, int limit);
}
