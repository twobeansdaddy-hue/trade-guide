package com.tradeguide.service.asset;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.AssetListingSource;
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

    /**
     * 증권사 개시 잔고 반영 전용 진입점이다. 연결된 증권사가 정상 보유 종목으로
     * 반환한 market/ticker/displayName은 그 자체로 신뢰 가능한 입력이므로,
     * 외부 종목 검색 성공 여부와 무관하게 로컬 자산 기준정보를 채운다.
     *
     * <p>이미 카탈로그에 있는 종목은 절대 덮어쓰지 않는다: 기존 표시명이 수동
     * 검증(외부 검색)으로 등록됐든 과거 증권사 스냅샷으로 등록됐든, 이후 증권사가
     * 다른 표시명을 보고해도 조용히 갱신하지 않는다. 갱신이 필요하면 별도 관리
     * 기능으로 명시적으로 처리한다. 로컬에 비활성으로 등록된 종목은 증권사가
     * 정상 보유로 보고하더라도 자동으로 재활성화하지 않고 즉시 거부한다 —
     * 비활성화는 대개 의도적인 운영 판단(상장폐지, 거래 제한 등)이며, 증권사
     * 스냅샷만으로 그 판단을 뒤집을 수 없다.
     */
    @Transactional
    public AssetListing ensureActiveListingFromBrokerSnapshot(Market market, String ticker, String displayName) {
        if (market == null) {
            throw new IllegalArgumentException("시장은 필수입니다.");
        }
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("종목 코드는 필수입니다.");
        }

        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        String normalizedDisplayName = (displayName == null || displayName.isBlank())
                ? normalizedTicker
                : displayName.trim();

        return assetListingRepository.findByMarketAndTicker(market, normalizedTicker)
                .map(listing -> requireActive(listing, market, normalizedTicker))
                .orElseGet(() -> createListingFromBrokerSnapshot(market, normalizedTicker, normalizedDisplayName));
    }

    private AssetListing createListingFromBrokerSnapshot(Market market, String ticker, String displayName) {
        AssetListing newListing = new AssetListing(
                market,
                ticker,
                displayName,
                ListingStatus.ACTIVE,
                AssetListingSource.BROKER_SNAPSHOT
        );

        return assetListingRepository.save(newListing);
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
