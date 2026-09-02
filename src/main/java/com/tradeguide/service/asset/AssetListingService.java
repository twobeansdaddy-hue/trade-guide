package com.tradeguide.service.asset;

import com.tradeguide.domain.asset.AssetSearchResult;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.asset.AssetListingRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

@Service
public class AssetListingService {

    private static final int MAX_SEARCH_RESULTS = 10;

    private final AssetListingRepository assetListingRepository;
    private final AssetSearchProvider assetSearchProvider;

    public AssetListingService(
            AssetListingRepository assetListingRepository,
            AssetSearchProvider assetSearchProvider
    ) {
        this.assetListingRepository = assetListingRepository;
        this.assetSearchProvider = assetSearchProvider;
    }

    public List<AssetSearchResult> searchActiveListings(Market market, String query) {
        if (market == null) {
            throw new IllegalArgumentException("시장은 필수입니다.");
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<AssetSearchResult> localResults = assetListingRepository
                .searchActiveListings(market, ListingStatus.ACTIVE, query.trim())
                .stream()
                .map(listing -> new AssetSearchResult(
                        listing.getMarket(),
                        listing.getTicker(),
                        listing.getDisplayName()
                ))
                .toList();
        List<AssetSearchResult> externalResults = assetSearchProvider
                .search(market, query.trim(), MAX_SEARCH_RESULTS);

        LinkedHashMap<String, AssetSearchResult> results = new LinkedHashMap<>();
        localResults.forEach(result -> results.put(result.ticker(), result));
        externalResults.forEach(result -> results.putIfAbsent(result.ticker(), result));

        return results.values().stream().limit(MAX_SEARCH_RESULTS).toList();
    }
}
