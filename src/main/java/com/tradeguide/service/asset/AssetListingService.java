package com.tradeguide.service.asset;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.asset.AssetListingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssetListingService {

    private static final int MAX_SEARCH_RESULTS = 10;

    private final AssetListingRepository assetListingRepository;

    public AssetListingService(AssetListingRepository assetListingRepository) {
        this.assetListingRepository = assetListingRepository;
    }

    public List<AssetListing> searchActiveListings(Market market, String query) {
        if (market == null) {
            throw new IllegalArgumentException("시장은 필수입니다.");
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return assetListingRepository
                .searchActiveListings(market, ListingStatus.ACTIVE, query.trim())
                .stream()
                .limit(MAX_SEARCH_RESULTS)
                .toList();
    }
}
