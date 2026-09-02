package com.tradeguide.service.asset;

import com.tradeguide.domain.asset.AssetSearchResult;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AssetSearchCacheTest {

    private final AssetSearchCache cache = new AssetSearchCache();

    @Test
    void reusesResultForSameMarketAndQuery() {
        AtomicInteger loadCount = new AtomicInteger();

        List<AssetSearchResult> first = cache.getOrLoad(
                Market.US,
                "aapl",
                () -> {
                    loadCount.incrementAndGet();
                    return List.of(new AssetSearchResult(Market.US, "AAPL", "Apple Inc."));
                }
        );
        List<AssetSearchResult> second = cache.getOrLoad(
                Market.US,
                "AAPL",
                () -> {
                    loadCount.incrementAndGet();
                    return List.of();
                }
        );

        assertThat(loadCount).hasValue(1);
        assertThat(second).isEqualTo(first);
    }
}
