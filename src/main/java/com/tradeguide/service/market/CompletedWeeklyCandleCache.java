package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class CompletedWeeklyCandleCache {

    private final WeeklyCandleSchedule weeklyCandleSchedule;
    private final Map<String, CacheEntry> cachedCandles = new ConcurrentHashMap<>();

    public CompletedWeeklyCandleCache(WeeklyCandleSchedule weeklyCandleSchedule) {
        this.weeklyCandleSchedule = weeklyCandleSchedule;
    }

    public List<MarketCandle> getOrLoad(
            Market market,
            String ticker,
            int outputSize,
            Supplier<List<MarketCandle>> loader
    ) {
        String cacheKey = market.name()
                + ":"
                + ticker.toUpperCase(Locale.ROOT)
                + ":"
                + outputSize;

        LocalDate expectedLatestCompletedCandleStart = weeklyCandleSchedule.getExpectedLatestCompletedCandleStart();

        CacheEntry cacheEntry = cachedCandles.get(cacheKey);

        if (cacheEntry != null && cacheEntry.latestCompletedCandleStart().equals(expectedLatestCompletedCandleStart)) {
            return cacheEntry.candles();
        }

        List<MarketCandle> loadedCandles = loader.get();

        cachedCandles.put(cacheKey, new CacheEntry(expectedLatestCompletedCandleStart, loadedCandles));

        return loadedCandles;
    }

    private record CacheEntry(
            LocalDate latestCompletedCandleStart,
            List<MarketCandle> candles
    ) {
    }

}
