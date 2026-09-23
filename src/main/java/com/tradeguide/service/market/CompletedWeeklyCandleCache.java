package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.trade.Market;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@Component
public class CompletedWeeklyCandleCache {

    public static String providerKey(MarketDataProvider provider, Long portfolioId) {
        return provider == MarketDataProvider.TOSS_SECURITIES
                ? provider.name() + ":" + portfolioId
                : provider.name();
    }

    private final WeeklyCandleSchedule weeklyCandleSchedule;
    private final Clock clock;
    private final Map<String, CacheEntry> cachedCandles = new ConcurrentHashMap<>();
    private final Map<InFlightKey, CompletableFuture<List<MarketCandle>>> inFlightLoads =
            new ConcurrentHashMap<>();

    @Autowired
    public CompletedWeeklyCandleCache(WeeklyCandleSchedule weeklyCandleSchedule, Clock clock) {
        this.weeklyCandleSchedule = weeklyCandleSchedule;
        this.clock = clock;
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
        return getOrLoadObserved(providerKey, market, ticker, outputSize, () -> {
            List<MarketCandle> loaded = loader.get();
            if (loaded == null) {
                throw new IllegalStateException("주봉 시세 데이터가 비어 있습니다.");
            }
            return new ObservedMarketCandles(loaded, Optional.empty());
        });
    }

    public List<MarketCandle> getOrLoadObserved(
            String providerKey,
            Market market,
            String ticker,
            int outputSize,
            Supplier<ObservedMarketCandles> loader
    ) {
        String cacheKey = cacheKey(providerKey, market, ticker, outputSize);

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
            ObservedMarketCandles observed = loader.get();
            if (observed == null || observed.candles() == null) {
                throw new IllegalStateException("주봉 시세 데이터가 비어 있습니다.");
            }
            List<MarketCandle> loadedCandles = observed.candles();

            cachedCandles.put(cacheKey, new CacheEntry(
                    expectedLatestCompletedCandleStart, loadedCandles, clock.instant(),
                    observed.sourceReceipt()));
            ownFuture.complete(loadedCandles);
            return loadedCandles;
        } catch (RuntimeException exception) {
            ownFuture.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightLoads.remove(inFlightKey, ownFuture);
        }
    }

    /** The timestamp belongs to the original load, including on later cache hits. */
    public Optional<CandleLoadObservation> findObservation(
            String providerKey, Market market, String ticker, int outputSize
    ) {
        CacheEntry entry = cachedCandles.get(cacheKey(providerKey, market, ticker, outputSize));
        if (entry == null || !entry.latestCompletedCandleStart()
                .equals(weeklyCandleSchedule.getExpectedLatestCompletedCandleStart())) {
            return Optional.empty();
        }
        return Optional.of(new CandleLoadObservation(
                entry.candles(), entry.loadCompletedAt(), entry.sourceReceipt()));
    }

    private String cacheKey(String providerKey, Market market, String ticker, int outputSize) {
        return market.name() + ":" + ticker.toUpperCase(Locale.ROOT) + ":" + outputSize + ":" + providerKey;
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
            List<MarketCandle> candles,
            Instant loadCompletedAt,
            Optional<ObservedMarketCandles.SourceReceipt> sourceReceipt
    ) {
    }

    public record CandleLoadObservation(
            List<MarketCandle> candles,
            Instant loadCompletedAt,
            Optional<ObservedMarketCandles.SourceReceipt> sourceReceipt
    ) {
        public CandleLoadObservation(List<MarketCandle> candles, Instant loadCompletedAt) {
            this(candles, loadCompletedAt, Optional.empty());
        }

        public CandleLoadObservation {
            candles = List.copyOf(candles);
            java.util.Objects.requireNonNull(sourceReceipt, "시세 수신 근거 상태가 필요합니다.");
        }
    }

    private record InFlightKey(
            String cacheKey,
            LocalDate latestCompletedCandleStart
    ) {
    }

}
