package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataUnavailableException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 종목별 현재가를 최대 {@link #CACHE_TTL} 동안 재사용한다.
 *
 * <p>동일한 키에 대한 캐시 미스가 동시에 발생해도 외부 제공자 호출은 단 한 번만
 * 수행한다(single-flight). 이 보장이 없으면 동시 요청마다 각각 외부 API를
 * 호출하게 되어, 실제 트래픽보다 훨씬 많은 요청이 순간적으로 몰려 429(Too Many
 * Requests)를 유발하거나 악화시킬 수 있다.
 *
 * <p>로딩이 실패하면 캐시에 값을 남기지 않는다. 오류를 조용히 감추고 과거 값을
 * 대신 제공하는 스테일 폴백은 별도 정책 결정 없이 도입하지 않는다.
 */
@Service
public class MarketPriceCache {

    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final Map<String, MarketPrice> cachedPrices =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<MarketPrice>> inFlightLoads =
            new ConcurrentHashMap<>();

    public MarketPrice getOrLoad(
            Market market,
            String ticker,
            Supplier<MarketPrice> loader
    ) {
        String cacheKey = buildCacheKey(market, ticker);

        MarketPrice cachedPrice = cachedPrices.get(cacheKey);
        if (isFresh(cachedPrice)) {
            return cachedPrice;
        }

        return loadSingleFlight(cacheKey, loader);
    }

    /**
     * 여러 종목의 현재가를 한 번에 조회한다. 이미 신선한 값이 있는 종목은 외부 호출 없이
     * 그대로 반환하고, 새로 불러와야 하는 종목 중 이 호출이 로딩 소유권을 얻은 종목만
     * 모아 {@code batchLoader}를 <b>정확히 한 번</b> 호출한다(요청 합치기). 다른 스레드가
     * 이미 로딩 중인 종목은 별도 호출 없이 그 결과를 기다린다.
     *
     * <p>{@code batchLoader}가 실패하면 이 호출이 소유권을 가진 모든 종목의 로딩이 함께
     * 실패한다. 부분 성공을 조용히 감추지 않고, 실패한 종목은 캐시에 남기지 않는다.
     * {@code batchLoader}가 반환한 맵에 없는 종목은 그 종목만 결과에서 빠진다(호출부가
     * 판단할 부분 실패이며, 이 캐시 계층의 오류는 아니다).
     *
     * @param batchLoader 이 호출이 실제로 불러와야 할 정규화된 티커 목록을 받아, 대문자로
     *                    정규화한 티커를 키로 하는 결과 맵을 돌려준다.
     */
    public Map<String, MarketPrice> getOrLoadBatch(
            Market market,
            List<String> tickers,
            Function<List<String>, Map<String, MarketPrice>> batchLoader
    ) {
        Map<String, MarketPrice> result = new LinkedHashMap<>();
        List<String> normalizedTickers = tickers.stream()
                .map(ticker -> ticker.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        List<String> ownedTickers = new ArrayList<>();
        Map<String, CompletableFuture<MarketPrice>> ownedFutures = new LinkedHashMap<>();
        Map<String, CompletableFuture<MarketPrice>> waitingFutures = new LinkedHashMap<>();

        for (String ticker : normalizedTickers) {
            String cacheKey = buildCacheKey(market, ticker);
            MarketPrice cachedPrice = cachedPrices.get(cacheKey);
            if (isFresh(cachedPrice)) {
                result.put(ticker, cachedPrice);
                continue;
            }

            CompletableFuture<MarketPrice> ownFuture = new CompletableFuture<>();
            CompletableFuture<MarketPrice> inFlightFuture = inFlightLoads.putIfAbsent(cacheKey, ownFuture);
            if (inFlightFuture != null) {
                waitingFutures.put(ticker, inFlightFuture);
            } else {
                ownedTickers.add(ticker);
                ownedFutures.put(ticker, ownFuture);
            }
        }

        if (!ownedTickers.isEmpty()) {
            try {
                Map<String, MarketPrice> loaded = batchLoader.apply(ownedTickers);

                ownedFutures.forEach((ticker, future) -> {
                    MarketPrice loadedPrice = loaded.get(ticker);
                    if (loadedPrice == null) {
                        // 이 종목만의 부분 실패다. 다른 종목의 로딩을 실패로 만들지 않는다.
                        future.complete(null);
                        return;
                    }
                    cachedPrices.put(buildCacheKey(market, ticker), loadedPrice);
                    future.complete(loadedPrice);
                    result.put(ticker, loadedPrice);
                });
            } catch (RuntimeException exception) {
                ownedFutures.values().forEach(future -> future.completeExceptionally(exception));
                throw exception;
            } finally {
                ownedTickers.forEach(ticker ->
                        inFlightLoads.remove(buildCacheKey(market, ticker), ownedFutures.get(ticker)));
            }
        }

        waitingFutures.forEach((ticker, future) -> {
            MarketPrice joinedPrice = join(future);
            if (joinedPrice != null) {
                result.put(ticker, joinedPrice);
            }
        });

        return result;
    }

    private MarketPrice loadSingleFlight(
            String cacheKey,
            Supplier<MarketPrice> loader
    ) {
        CompletableFuture<MarketPrice> ownFuture = new CompletableFuture<>();
        CompletableFuture<MarketPrice> inFlightFuture =
                inFlightLoads.putIfAbsent(cacheKey, ownFuture);

        if (inFlightFuture != null) {
            // 다른 스레드가 같은 키를 이미 로딩 중이다. 별도 호출 없이 그 결과를 기다린다.
            // 그 로딩이 배치 호출 소유였고 이 종목만 부분 실패했다면(getOrLoadBatch 참고)
            // 결과가 null일 수 있다. 단건 조회 계약은 null을 반환하지 않으므로 여기서 막는다.
            MarketPrice joinedPrice = join(inFlightFuture);
            if (joinedPrice == null) {
                throw new MarketDataUnavailableException("현재가를 조회하지 못했습니다: " + cacheKey);
            }
            return joinedPrice;
        }

        try {
            MarketPrice loadedPrice = loader.get();
            cachedPrices.put(cacheKey, loadedPrice);
            ownFuture.complete(loadedPrice);
            return loadedPrice;
        } catch (RuntimeException exception) {
            ownFuture.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightLoads.remove(cacheKey, ownFuture);
        }
    }

    private MarketPrice join(CompletableFuture<MarketPrice> future) {
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

    private boolean isFresh(MarketPrice cachedPrice) {
        return cachedPrice != null
                && cachedPrice.getCapturedAt()
                .plus(CACHE_TTL)
                .isAfter(Instant.now());
    }

    private String buildCacheKey(Market market, String ticker) {
        return market.name() + ":" + ticker.toUpperCase(Locale.ROOT);
    }
}
