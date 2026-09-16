package com.tradeguide.service.market;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Twelve Data의 공식 다종목 배치 계약({@code GET /price?symbol=A,B,C})으로 현재가를 조회한다.
 *
 * <p>배치 호출은 외부 HTTP 요청 횟수만 줄인다. Twelve Data는 배치로 조회해도 <b>심볼당
 * 크레딧을 그대로 소비</b>하므로({@link TwelveDataRateLimitTracker}가 심볼 수 기준으로
 * 사용량을 기록하는 이유), 이 배치화를 크레딧 절감으로 오해하면 안 된다. 실제 절감은
 * {@link MarketPriceCache}의 종목별 TTL 재사용에서 온다.
 *
 * <p>429를 받으면 {@link TwelveDataRateLimitTracker}에 전역 쿨다운을 등록하고, 쿨다운이
 * 끝나기 전의 후속 호출은 외부 호출 없이 즉시 거부한다.
 */
@Service
public class TwelveDataMarketPriceProvider implements MarketPriceProvider {

    /**
     * 한 배치 요청에 담는 최대 심볼 수. Twelve Data가 문서화한 상한이 아니라, URL 길이와
     * 한 번의 429가 미치는 영향 범위를 보수적으로 제한하려는 우리 쪽 기본값이다.
     */
    private static final int MAX_SYMBOLS_PER_BATCH = 100;

    private final RestClient restClient;
    private final String apiKey;
    private final MarketPriceCache marketPriceCache;
    private final MarketDataProviderConfigurationStatus configurationStatus;
    private final TwelveDataRateLimitTracker rateLimitTracker;

    public TwelveDataMarketPriceProvider(
            RestClient.Builder restClientBuilder,
            @Value("${twelve-data.api-key}") String apiKey,
            MarketPriceCache marketPriceCache,
            MarketDataProviderConfigurationStatus configurationStatus,
            TwelveDataRateLimitTracker rateLimitTracker
    ) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.twelvedata.com")
                .build();
        this.apiKey = apiKey;
        this.marketPriceCache = marketPriceCache;
        this.configurationStatus = configurationStatus;
        this.rateLimitTracker = rateLimitTracker;
    }

    @Override
    public MarketDataProvider getProvider() {
        return MarketDataProvider.TWELVE_DATA;
    }

    @Override
    public MarketPrice getCurrentPrice(Market market, String ticker) {
        return marketPriceCache.getOrLoad(
                market,
                ticker,
                () -> loadSingleFromTwelveData(market, ticker.toUpperCase(Locale.ROOT))
        );
    }

    @Override
    public Map<String, MarketPrice> getCurrentPrices(Market market, List<String> tickers) {
        if (tickers.isEmpty()) {
            return Map.of();
        }

        return marketPriceCache.getOrLoadBatch(
                market,
                tickers,
                missingTickers -> loadBatchFromTwelveData(market, missingTickers)
        );
    }

    private MarketPrice loadSingleFromTwelveData(Market market, String normalizedTicker) {
        Map<String, MarketPrice> loaded = loadBatchFromTwelveData(market, List.of(normalizedTicker));
        MarketPrice price = loaded.get(normalizedTicker);
        if (price == null) {
            throw new MarketDataUnavailableException("현재가를 찾을 수 없습니다: " + normalizedTicker);
        }
        return price;
    }

    /**
     * 실제로 불러와야 할(캐시에 없는) 심볼만 받아 Twelve Data를 호출한다.
     * 심볼 수가 {@link #MAX_SYMBOLS_PER_BATCH}를 넘으면 여러 요청으로 나눠 호출한다.
     */
    private Map<String, MarketPrice> loadBatchFromTwelveData(Market market, List<String> tickers) {
        configurationStatus.requireConfigured(MarketDataProvider.TWELVE_DATA);
        rateLimitTracker.requireNotCoolingDown();

        Map<String, MarketPrice> result = new LinkedHashMap<>();
        for (int start = 0; start < tickers.size(); start += MAX_SYMBOLS_PER_BATCH) {
            List<String> chunk = tickers.subList(start, Math.min(start + MAX_SYMBOLS_PER_BATCH, tickers.size()));
            result.putAll(callTwelveDataPriceEndpoint(market, chunk));
        }
        return result;
    }

    private Map<String, MarketPrice> callTwelveDataPriceEndpoint(Market market, List<String> normalizedTickers) {
        String symbolParam = String.join(",", normalizedTickers);
        Instant capturedAt = Instant.now();

        TwelveDataPriceResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/price")
                            .queryParam("symbol", symbolParam)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "apikey " + apiKey)
                    .retrieve()
                    .body(TwelveDataPriceResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                Duration retryAfter = parseRetryAfter(exception);
                rateLimitTracker.recordRateLimited(retryAfter);
                throw new MarketDataRateLimitExceededException(
                        "현재가 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
                        exception,
                        retryAfter != null ? retryAfter.getSeconds() : null
                );
            }

            throw new MarketDataUnavailableException(
                    "현재가 조회에 실패했습니다.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new MarketDataUnavailableException(
                    "현재가 조회에 실패했습니다.",
                    exception
            );
        }

        if (response == null || response.isEmpty()) {
            throw new MarketDataUnavailableException(
                    "현재가 응답이 올바르지 않습니다."
            );
        }

        rateLimitTracker.recordSuccess(normalizedTickers.size());

        Map<String, MarketPrice> result = new LinkedHashMap<>();
        for (String ticker : normalizedTickers) {
            String rawPrice = response.priceFor(ticker, normalizedTickers.size());
            if (rawPrice == null) {
                // 이 심볼만 응답에 없거나 오류 항목이다. 다른 심볼 결과에는 영향을 주지 않는다.
                continue;
            }
            try {
                result.put(ticker, new MarketPrice(market, ticker, new BigDecimal(rawPrice), market.getCurrency(), capturedAt));
            } catch (NumberFormatException exception) {
                // 형식이 잘못된 심볼도 같은 방식으로, 그 심볼만 결과에서 뺀다.
            }
        }
        return result;
    }

    /**
     * {@code Retry-After} 헤더를 읽는다. 초 단위 정수만 지원한다(HTTP-date 형식은 Twelve
     * Data 429 응답에서 관측된 바 없어 지원 범위를 넓히지 않는다). 헤더가 없거나 형식이
     * 다르면 {@code null}을 반환해 호출부가 우리 쪽 기본 쿨다운을 쓰게 한다.
     */
    private Duration parseRetryAfter(RestClientResponseException exception) {
        String headerValue = exception.getResponseHeaders() != null
                ? exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)
                : null;
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(headerValue.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Twelve Data {@code /price} 응답의 두 가지 형태를 모두 담는다.
     * 심볼 하나를 요청하면 {@code {"price": "..."}} 형태의 평면 객체를,
     * 여러 심볼을 요청하면 {@code {"AAPL": {"price": "..."}, "MSFT": {...}}} 형태로
     * 심볼별 객체를 중첩해 돌려준다. {@link #priceFor}가 두 형태를 함께 처리한다.
     */
    private static final class TwelveDataPriceResponse {
        private String price;
        private final Map<String, Map<String, Object>> perSymbol = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        @JsonAnySetter
        void setAny(String key, Object value) {
            if ("price".equals(key) && value instanceof String stringValue) {
                this.price = stringValue;
                return;
            }
            if (value instanceof Map<?, ?> mapValue) {
                perSymbol.put(key.toUpperCase(Locale.ROOT), (Map<String, Object>) mapValue);
            }
        }

        boolean isEmpty() {
            return price == null && perSymbol.isEmpty();
        }

        String priceFor(String ticker, int requestedSymbolCount) {
            if (requestedSymbolCount == 1 && price != null) {
                return price;
            }
            Map<String, Object> symbolEntry = perSymbol.get(ticker);
            if (symbolEntry == null) {
                return null;
            }
            Object rawPrice = symbolEntry.get("price");
            return rawPrice instanceof String stringPrice ? stringPrice : null;
        }
    }
}
