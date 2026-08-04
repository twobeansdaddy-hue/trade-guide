package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class MarketPriceCache {

    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final Map<String, MarketPrice> cachedPrices =
            new ConcurrentHashMap<>();

    public MarketPrice getOrLoad(
            Market market,
            String ticker,
            Supplier<MarketPrice> loader
    ) {
        String cacheKey = market.name() + ":"
                + ticker.toUpperCase(Locale.ROOT);

        MarketPrice cachedPrice = cachedPrices.get(cacheKey);

        if (cachedPrice != null
                && cachedPrice.getCapturedAt()
                .plus(CACHE_TTL)
                .isAfter(Instant.now())) {
            return cachedPrice;
        }

        MarketPrice loadedPrice = loader.get();
        cachedPrices.put(cacheKey, loadedPrice);

        return loadedPrice;
    }
}
