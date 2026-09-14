package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void coalescesConcurrentCacheMissesIntoASingleLoad() throws InterruptedException {
        int callerCount = 20;
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch callersReady = new CountDownLatch(callerCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);

        try {
            List<Future<MarketPrice>> futures = IntStream.range(0, callerCount)
                    .mapToObj(i -> executor.submit(() -> {
                        callersReady.countDown();
                        awaitStartSignal(startSignal);
                        return cache.getOrLoad(
                                Market.US,
                                "AAPL",
                                () -> {
                                    loadCount.incrementAndGet();
                                    sleepBriefly();
                                    return new MarketPrice(
                                            Market.US,
                                            "AAPL",
                                            new BigDecimal("210.50"),
                                            Instant.now()
                                    );
                                }
                        );
                    }))
                    .toList();

            callersReady.await(5, TimeUnit.SECONDS);
            startSignal.countDown();

            Set<MarketPrice> results = futures.stream()
                    .map(this::joinUninterruptibly)
                    .collect(Collectors.toSet());

            assertThat(loadCount).hasValue(1);
            assertThat(results).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void doesNotCacheAFailedLoadSoASubsequentCallRetries() {
        AtomicInteger loadCount = new AtomicInteger();
        RuntimeException rateLimitError = new RuntimeException("429");

        assertThatThrownBy(() -> cache.getOrLoad(
                Market.US,
                "AAPL",
                () -> {
                    loadCount.incrementAndGet();
                    throw rateLimitError;
                }
        )).isSameAs(rateLimitError);

        MarketPrice recoveredPrice = new MarketPrice(
                Market.US,
                "AAPL",
                new BigDecimal("210.50"),
                Instant.now()
        );
        MarketPrice result = cache.getOrLoad(
                Market.US,
                "AAPL",
                () -> {
                    loadCount.incrementAndGet();
                    return recoveredPrice;
                }
        );

        assertThat(result).isSameAs(recoveredPrice);
        assertThat(loadCount).hasValue(2);
    }

    @Test
    void concurrentCallersJoiningAFailedLoadAllReceiveTheFailure() throws InterruptedException {
        int callerCount = 10;
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch callersReady = new CountDownLatch(callerCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        RuntimeException rateLimitError = new RuntimeException("429");
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);

        try {
            List<Future<MarketPrice>> futures = IntStream.range(0, callerCount)
                    .mapToObj(i -> executor.submit(() -> {
                        callersReady.countDown();
                        awaitStartSignal(startSignal);
                        return cache.getOrLoad(
                                Market.US,
                                "AAPL",
                                () -> {
                                    loadCount.incrementAndGet();
                                    sleepBriefly();
                                    throw rateLimitError;
                                }
                        );
                    }))
                    .toList();

            callersReady.await(5, TimeUnit.SECONDS);
            startSignal.countDown();

            for (Future<MarketPrice> future : futures) {
                assertThatThrownBy(() -> joinUninterruptibly(future))
                        .isSameAs(rateLimitError);
            }

            assertThat(loadCount).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void batchLoadsOnlyTheMissingTickersInOneCallAndReusesFreshCacheForTheRest() {
        AtomicInteger batchCallCount = new AtomicInteger();
        MarketPrice cachedAapl = new MarketPrice(
                Market.US, "AAPL", new BigDecimal("210.50"), Instant.now()
        );
        cache.getOrLoad(Market.US, "AAPL", () -> cachedAapl);

        Map<String, MarketPrice> result = cache.getOrLoadBatch(
                Market.US,
                List.of("AAPL", "MSFT"),
                missingTickers -> {
                    batchCallCount.incrementAndGet();
                    assertThat(missingTickers).containsExactly("MSFT");
                    return Map.of("MSFT", new MarketPrice(
                            Market.US, "MSFT", new BigDecimal("400.10"), Instant.now()
                    ));
                }
        );

        assertThat(batchCallCount).hasValue(1);
        assertThat(result.get("AAPL")).isSameAs(cachedAapl);
        assertThat(result.get("MSFT").getCurrentPrice()).isEqualByComparingTo("400.10");
    }

    @Test
    void omitsTickerMissingFromBatchLoaderResultWithoutFailingOthers() {
        Map<String, MarketPrice> result = cache.getOrLoadBatch(
                Market.US,
                List.of("AAPL", "BADSYM"),
                missingTickers -> Map.of(
                        "AAPL", new MarketPrice(Market.US, "AAPL", new BigDecimal("210.50"), Instant.now())
                )
        );

        assertThat(result).containsOnlyKeys("AAPL");
    }

    @Test
    void concurrentBatchCallsForTheSameTickerTriggerOnlyOneLoad() throws InterruptedException {
        int callerCount = 20;
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch callersReady = new CountDownLatch(callerCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);

        try {
            List<Future<Map<String, MarketPrice>>> futures = IntStream.range(0, callerCount)
                    .mapToObj(i -> executor.submit(() -> {
                        callersReady.countDown();
                        awaitStartSignal(startSignal);
                        return cache.getOrLoadBatch(
                                Market.US,
                                List.of("AAPL"),
                                missingTickers -> {
                                    loadCount.incrementAndGet();
                                    sleepBriefly();
                                    return Map.of("AAPL", new MarketPrice(
                                            Market.US, "AAPL", new BigDecimal("210.50"), Instant.now()
                                    ));
                                }
                        );
                    }))
                    .toList();

            callersReady.await(5, TimeUnit.SECONDS);
            startSignal.countDown();

            for (Future<Map<String, MarketPrice>> future : futures) {
                assertThat(joinMapUninterruptibly(future)).containsKey("AAPL");
            }

            assertThat(loadCount).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitStartSignal(CountDownLatch startSignal) {
        try {
            startSignal.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private MarketPrice joinUninterruptibly(Future<MarketPrice> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Map<String, MarketPrice> joinMapUninterruptibly(Future<Map<String, MarketPrice>> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException(exception);
        }
    }
}