package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompletedWeeklyCandleCacheTest {

    @Mock
    private WeeklyCandleSchedule weeklyCandleSchedule;

    private CompletedWeeklyCandleCache completedWeeklyCandleCache;

    @BeforeEach
    void setUp() {
        completedWeeklyCandleCache = new CompletedWeeklyCandleCache(
                weeklyCandleSchedule,
                Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC));
    }

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
        assertThat(completedWeeklyCandleCache.findObservation("DEFAULT", Market.US, "TQQQ", 101))
                .get()
                .extracting(CompletedWeeklyCandleCache.CandleLoadObservation::loadCompletedAt)
                .isEqualTo(Instant.parse("2026-08-10T12:00:00Z"));
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

    @Test
    void cacheHitRetainsOriginalLoadTime() {
        Clock clock = mock(Clock.class);
        Instant firstLoad = Instant.parse("2026-08-10T12:00:00Z");
        when(clock.instant()).thenReturn(firstLoad);
        completedWeeklyCandleCache = new CompletedWeeklyCandleCache(weeklyCandleSchedule, clock);
        when(weeklyCandleSchedule.getExpectedLatestCompletedCandleStart())
                .thenReturn(LocalDate.of(2026, 8, 3));

        completedWeeklyCandleCache.getOrLoad("TWELVE_DATA", Market.US, "SOXL", 101, List::of);
        completedWeeklyCandleCache.getOrLoad("TWELVE_DATA", Market.US, "SOXL", 101, List::of);

        assertThat(completedWeeklyCandleCache.findObservation("TWELVE_DATA", Market.US, "SOXL", 101))
                .get()
                .extracting(CompletedWeeklyCandleCache.CandleLoadObservation::loadCompletedAt)
                .isEqualTo(firstLoad);
        verify(clock).instant();
        verifyNoMoreInteractions(clock);
    }

    @Test
    void failedLoadHasNoObservation() {
        when(weeklyCandleSchedule.getExpectedLatestCompletedCandleStart())
                .thenReturn(LocalDate.of(2026, 8, 3));

        assertThatThrownBy(() -> completedWeeklyCandleCache.getOrLoad(
                "TWELVE_DATA", Market.US, "SOXL", 101,
                () -> { throw new IllegalStateException("provider failed"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(completedWeeklyCandleCache.findObservation("TWELVE_DATA", Market.US, "SOXL", 101))
                .isEmpty();
    }

    @Test
    void isolatesTossCandlesByPortfolioButSharesPublicProviderCandles() {
        when(weeklyCandleSchedule.getExpectedLatestCompletedCandleStart())
                .thenReturn(LocalDate.of(2026, 8, 3));
        AtomicInteger loads = new AtomicInteger();
        String firstToss = CompletedWeeklyCandleCache.providerKey(MarketDataProvider.TOSS_SECURITIES, 1L);
        String secondToss = CompletedWeeklyCandleCache.providerKey(MarketDataProvider.TOSS_SECURITIES, 2L);
        String publicProvider = CompletedWeeklyCandleCache.providerKey(MarketDataProvider.TWELVE_DATA, 1L);

        completedWeeklyCandleCache.getOrLoad(firstToss, Market.US, "SOXL", 101,
                () -> { loads.incrementAndGet(); return List.of(); });
        completedWeeklyCandleCache.getOrLoad(secondToss, Market.US, "SOXL", 101,
                () -> { loads.incrementAndGet(); return List.of(); });
        completedWeeklyCandleCache.getOrLoad(publicProvider, Market.US, "SOXL", 101,
                () -> { loads.incrementAndGet(); return List.of(); });
        completedWeeklyCandleCache.getOrLoad(
                CompletedWeeklyCandleCache.providerKey(MarketDataProvider.TWELVE_DATA, 2L),
                Market.US, "SOXL", 101,
                () -> { loads.incrementAndGet(); return List.of(); });

        assertThat(loads).hasValue(3);
        assertThat(firstToss).isNotEqualTo(secondToss);
        assertThat(completedWeeklyCandleCache.findObservation(secondToss, Market.US, "SOXL", 101))
                .isPresent();
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
