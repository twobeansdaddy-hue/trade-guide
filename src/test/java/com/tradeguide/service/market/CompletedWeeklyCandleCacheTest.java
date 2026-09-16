package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompletedWeeklyCandleCacheTest {

    @Mock
    private WeeklyCandleSchedule weeklyCandleSchedule;

    @InjectMocks
    private CompletedWeeklyCandleCache completedWeeklyCandleCache;

    @Test
    void returnsCachedCandlesWithinSameCompletedWeek() {
        when(weeklyCandleSchedule.getExpectedLatestCompletedCandleStart())
                .thenReturn(LocalDate.of(2026, 8, 3));

        AtomicInteger loadCount = new AtomicInteger();
        List<MarketCandle> cachedCandles = List.of();

        List<MarketCandle> first = completedWeeklyCandleCache.getOrLoad(
                Market.US,
                "tqqq",
                101,
                () -> {
                    loadCount.incrementAndGet();
                    return cachedCandles;
                }
        );

        List<MarketCandle> second = completedWeeklyCandleCache.getOrLoad(
                Market.US,
                "TQQQ",
                101,
                () -> {
                    loadCount.incrementAndGet();
                    return List.of();
                }
        );

        assertThat(first).isSameAs(cachedCandles);
        assertThat(second).isSameAs(cachedCandles);
        assertThat(loadCount).hasValue(1);
    }

    @Test
    void reloadsCandlesWhenLatestCompletedWeekChanges() {
        when(weeklyCandleSchedule.getExpectedLatestCompletedCandleStart())
                .thenReturn(
                        LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 10)
                );

        AtomicInteger loadCount = new AtomicInteger();
        List<MarketCandle> previousWeekCandles = List.of();
        List<MarketCandle> currentWeekCandles = List.of();

        completedWeeklyCandleCache.getOrLoad(
                Market.US,
                "TQQQ",
                101,
                () -> {
                    loadCount.incrementAndGet();
                    return previousWeekCandles;
                }
        );

        List<MarketCandle> result = completedWeeklyCandleCache.getOrLoad(
                Market.US,
                "TQQQ",
                101,
                () -> {
                    loadCount.incrementAndGet();
                    return currentWeekCandles;
                }
        );

        assertThat(result).isSameAs(currentWeekCandles);
        assertThat(loadCount).hasValue(2);
    }

    @Test
    void mergesConcurrentLoadsForSameTickerAndCompletedWeek() throws Exception {
        when(weeklyCandleSchedule.getExpectedLatestCompletedCandleStart())
                .thenReturn(LocalDate.of(2026, 8, 3));

        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        List<MarketCandle> loadedCandles = List.of();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<List<MarketCandle>> first = executor.submit(() -> completedWeeklyCandleCache.getOrLoad(
                    Market.US,
                    "SOXL",
                    101,
                    () -> {
                        loadCount.incrementAndGet();
                        loaderStarted.countDown();
                        await(releaseLoader);
                        return loadedCandles;
                    }
            ));

            assertThat(loaderStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Future<List<MarketCandle>> second = executor.submit(() -> completedWeeklyCandleCache.getOrLoad(
                    Market.US,
                    "soxl",
                    101,
                    () -> {
                        loadCount.incrementAndGet();
                        return List.of();
                    }
            ));

            releaseLoader.countDown();

            assertThat(first.get(1, TimeUnit.SECONDS)).isSameAs(loadedCandles);
            assertThat(second.get(1, TimeUnit.SECONDS)).isSameAs(loadedCandles);
            assertThat(loadCount).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("테스트 로더가 중단되었습니다.", exception);
        }
    }
}
