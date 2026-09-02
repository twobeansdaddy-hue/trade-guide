package com.tradeguide.service.asset;

import com.tradeguide.domain.asset.AssetSearchResult;
import com.tradeguide.domain.trade.Market;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class AssetSearchCache {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final Map<String, CacheEntry> cachedResults = new ConcurrentHashMap<>();

    public List<AssetSearchResult> getOrLoad(
            Market market,
            String query,
            Supplier<List<AssetSearchResult>> loader
    ) {
        String cacheKey = market.name() + ":" + query.toUpperCase(Locale.ROOT);
        CacheEntry cachedResult = cachedResults.get(cacheKey);

        if (cachedResult != null && cachedResult.loadedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cachedResult.results();
        }

        List<AssetSearchResult> loadedResults = loader.get();
        cachedResults.put(cacheKey, new CacheEntry(loadedResults, Instant.now()));

        return loadedResults;
    }

    private record CacheEntry(List<AssetSearchResult> results, Instant loadedAt) {
    }
}
