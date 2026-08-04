package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MarketPriceCacheTest {

    private final MarketPriceCache cache = new MarketPriceCache();

    @Test
    void returnsCachedPriceBeforeTtlExpires() {
        AtomicInteger loadCount = new AtomicInteger();
        MarketPrice marketPrice = new MarketPrice(
                Market.US,
                "AAPL",
                new BigDecimal("210.50"),
                Instant.now()
        );

        MarketPrice first = cache.getOrLoad(
                Market.US,
                "aapl",
                () -> {
                    loadCount.incrementAndGet();
                    return marketPrice;
                }
        );
        MarketPrice second = cache.getOrLoad(
                Market.US,
                "AAPL",
                () -> {
                    loadCount.incrementAndGet();
                    return new MarketPrice(
                            Market.US,
                            "AAPL",
                            new BigDecimal("999.99"),
                            Instant.now()
                    );
                }
        );

        assertThat(first).isSameAs(marketPrice);
        assertThat(second).isSameAs(marketPrice);
        assertThat(loadCount).hasValue(1);
    }

    @Test
    void reloadsPriceWhenTtlExpires() {
        AtomicInteger loadCount = new AtomicInteger();
        MarketPrice expiredPrice = new MarketPrice(
                Market.US,
                "AAPL",
                new BigDecimal("210.50"),
                Instant.now().minus(Duration.ofMinutes(2))
        );
        MarketPrice refreshedPrice = new MarketPrice(
                Market.US,
                "AAPL",
                new BigDecimal("211.00"),
                Instant.now()
        );

        cache.getOrLoad(
                Market.US,
                "AAPL",
                () -> {
                    loadCount.incrementAndGet();
                    return expiredPrice;
                }
        );

        MarketPrice result = cache.getOrLoad(
                Market.US,
                "AAPL",
                () -> {
                    loadCount.incrementAndGet();
                    return refreshedPrice;
                }
        );

        assertThat(result).isSameAs(refreshedPrice);
        assertThat(loadCount).hasValue(2);
    }
}