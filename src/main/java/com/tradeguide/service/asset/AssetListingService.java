package com.tradeguide.service.asset;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.AssetSearchResult;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.asset.AssetListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class AssetListingService {

    private static final int MAX_SEARCH_RESULTS = 10;

    private final AssetListingRepository assetListingRepository;
    private final AssetSearchProvider assetSearchProvider;
    private final AssetSearchCache assetSearchCache;

    public AssetListingService(
            AssetListingRepository assetListingRepository,
            AssetSearchProvider assetSearchProvider,
            AssetSearchCache assetSearchCache
    ) {
        this.assetListingRepository = assetListingRepository;
        this.assetSearchProvider = assetSearchProvider;
        this.assetSearchCache = assetSearchCache;
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
        List<AssetSearchResult> externalResults = assetSearchCache.getOrLoad(
                market,
                query.trim(),
                () -> assetSearchProvider.search(
                        market,
                        query.trim(),
                        MAX_SEARCH_RESULTS
                )
        );

        LinkedHashMap<String, AssetSearchResult> results = new LinkedHashMap<>();
        localResults.forEach(result -> results.put(result.ticker(), result));
        externalResults.forEach(result -> results.putIfAbsent(result.ticker(), result));

        return results.values().stream().limit(MAX_SEARCH_RESULTS).toList();
    }

    @Transactional
    public AssetListing ensureActiveListingForTrade(Market market, String ticker) {
        if (market == null) {
            throw new IllegalArgumentException("시장은 필수입니다.");
        }
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("종목 코드는 필수입니다.");
        }

        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);

        return assetListingRepository.findByMarketAndTicker(market, normalizedTicker)
                .map(listing -> requireActive(listing, market, normalizedTicker))
                .orElseGet(() -> createActiveListingFromSearchResult(market, normalizedTicker));
    }

    private AssetListing requireActive(AssetListing listing, Market market, String ticker) {
        if (listing.getListingStatus() != ListingStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "비활성 상장 종목은 거래를 등록할 수 없습니다: " + market + " / " + ticker
            );
        }
        return listing;
    }

    private AssetListing createActiveListingFromSearchResult(Market market, String ticker) {
        List<AssetSearchResult> searchResults = assetSearchCache.getOrLoad(
                market,
                ticker,
                () -> assetSearchProvider.search(market, ticker, MAX_SEARCH_RESULTS)
        );

        AssetSearchResult match = searchResults.stream()
                .filter(result -> result.market() == market
                        && result.ticker().equalsIgnoreCase(ticker))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래 가능한 상장 정보를 찾을 수 없습니다: " + market + " / " + ticker
                ));

        AssetListing newListing = new AssetListing(
                market,
                ticker,
                match.displayName(),
                ListingStatus.ACTIVE
        );

        return assetListingRepository.save(newListing);
    }
}
