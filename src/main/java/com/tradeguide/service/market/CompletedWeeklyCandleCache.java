package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@Component
public class CompletedWeeklyCandleCache {

    private final WeeklyCandleSchedule weeklyCandleSchedule;
    private final Map<String, CacheEntry> cachedCandles = new ConcurrentHashMap<>();
    private final Map<InFlightKey, CompletableFuture<List<MarketCandle>>> inFlightLoads =
            new ConcurrentHashMap<>();

    public CompletedWeeklyCandleCache(WeeklyCandleSchedule weeklyCandleSchedule) {
        this.weeklyCandleSchedule = weeklyCandleSchedule;
    }

    public List<MarketCandle> getOrLoad(
            Market market,
            String ticker,
            int outputSize,
            Supplier<List<MarketCandle>> loader
    ) {
        return getOrLoad("DEFAULT", market, ticker, outputSize, loader);
    }

    /**
     * 제공자별로 주봉 캐시를 분리한다. 같은 티커라도 Twelve Data와 토스의 원천 데이터는
     * 서로 다른 데이터셋이므로, 제공자를 바꾼 뒤 이전 제공자의 캐시를 재사용하면 설정과
     * 화면의 근거가 어긋난다.
     */
    public List<MarketCandle> getOrLoad(
            String providerKey,
            Market market,
            String ticker,
            int outputSize,
            Supplier<List<MarketCandle>> loader
    ) {
        String cacheKey = market.name()
                + ":"
                + ticker.toUpperCase(Locale.ROOT)
                + ":"
                + outputSize
                + ":"
                + providerKey;

        LocalDate expectedLatestCompletedCandleStart = weeklyCandleSchedule.getExpectedLatestCompletedCandleStart();

        CacheEntry cacheEntry = cachedCandles.get(cacheKey);

        if (cacheEntry != null && cacheEntry.latestCompletedCandleStart().equals(expectedLatestCompletedCandleStart)) {
            return cacheEntry.candles();
        }

        InFlightKey inFlightKey = new InFlightKey(cacheKey, expectedLatestCompletedCandleStart);
        CompletableFuture<List<MarketCandle>> ownFuture = new CompletableFuture<>();
        CompletableFuture<List<MarketCandle>> inFlightFuture = inFlightLoads.putIfAbsent(inFlightKey, ownFuture);

        if (inFlightFuture != null) {
            return join(inFlightFuture);
        }

        try {
            List<MarketCandle> loadedCandles = loader.get();
            if (loadedCandles == null) {
                throw new IllegalStateException("주봉 시세 데이터가 비어 있습니다.");
            }

            cachedCandles.put(cacheKey, new CacheEntry(expectedLatestCompletedCandleStart, loadedCandles));
            ownFuture.complete(loadedCandles);
            return loadedCandles;
        } catch (RuntimeException exception) {
            ownFuture.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightLoads.remove(inFlightKey, ownFuture);
        }
    }

    private List<MarketCandle> join(CompletableFuture<List<MarketCandle>> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private record CacheEntry(
            LocalDate latestCompletedCandleStart,
            List<MarketCandle> candles
    ) {
    }

    private record InFlightKey(
            String cacheKey,
            LocalDate latestCompletedCandleStart
    ) {
    }

}
