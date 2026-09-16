package com.tradeguide.service.broker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataProviderAccessDeniedException;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 토스증권의 일봉 캔들을 조회한다. 토스는 주봉을 별도 간격으로 제공하지 않으므로,
 * 전략 엔진이 요구하는 주봉은 일봉 OHLCV를 거래 주간 단위로 집계한다.
 */
@Component
public class TossSecuritiesMarketHistoryProvider {

    private static final int MAX_CANDLES_PER_REQUEST = 200;
    private static final int MAX_DAILY_CANDLES = 5000;
    private static final String ACCOUNT_CURRENCY = "USD";

    private final RestClient restClient;
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer;

    public TossSecuritiesMarketHistoryProvider(
            @Qualifier("brokerRestClientBuilder") RestClient.Builder builder,
            @Value("${toss-securities.base-url:https://openapi.tossinvest.com}") String baseUrl,
            TossSecuritiesAccessTokenIssuer accessTokenIssuer
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.accessTokenIssuer = accessTokenIssuer;
    }

    public List<MarketCandle> getCandles(
            BrokerCredentials credentials,
            Market market,
            String ticker,
            CandleInterval interval,
            int outputSize
    ) {
        if (outputSize < 1 || outputSize > MAX_DAILY_CANDLES) {
            throw new IllegalArgumentException("캔들 조회 개수는 1에서 5000 사이여야 합니다.");
        }

        int dailySize = interval == CandleInterval.WEEKLY
                ? Math.min(MAX_DAILY_CANDLES, Math.max(MAX_CANDLES_PER_REQUEST, outputSize * 7 + 30))
                : outputSize;
        List<MarketCandle> dailyCandles = loadDailyCandles(
                credentials,
                market,
                ticker,
                dailySize
        );

        if (interval == CandleInterval.DAILY) {
            return dailyCandles.stream()
                    .sorted(Comparator.comparing(MarketCandle::getTradingDate))
                    .limit(outputSize)
                    .toList();
        }

        return aggregateWeekly(dailyCandles, market, ticker, outputSize);
    }

    private List<MarketCandle> loadDailyCandles(
            BrokerCredentials credentials,
            Market market,
            String ticker,
            int outputSize
    ) {
        String accessToken = accessTokenIssuer.issueAccessToken(credentials);
        String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
        List<MarketCandle> candles = new ArrayList<>();
        String before = null;

        while (candles.size() < outputSize) {
            int count = Math.min(MAX_CANDLES_PER_REQUEST, outputSize - candles.size());
            CandlesResponse response = callCandlesEndpoint(
                    credentials,
                    accessToken,
                    market,
                    normalizedTicker,
                    count,
                    before
            );

            if (response == null || response.result() == null || response.result().candles() == null) {
                throw new MarketDataUnavailableException("토스증권 캔들 응답이 올바르지 않습니다.");
            }

            List<MarketCandle> page = response.result().candles().stream()
                    .filter(Objects::nonNull)
                    .map(item -> toMarketCandle(item, market, normalizedTicker))
                    .filter(Objects::nonNull)
                    .toList();
            candles.addAll(page);

            String nextBefore = response.result().nextBefore();
            if (page.isEmpty() || nextBefore == null || nextBefore.isBlank() || nextBefore.equals(before)) {
                break;
            }
            before = nextBefore;
        }

        if (candles.isEmpty()) {
            throw new MarketDataUnavailableException("토스증권 캔들 데이터를 찾을 수 없습니다.");
        }

        return candles.stream()
                .sorted(Comparator.comparing(MarketCandle::getTradingDate))
                .distinct()
                .toList();
    }

    private CandlesResponse callCandlesEndpoint(
            BrokerCredentials credentials,
            String accessToken,
            Market market,
            String ticker,
            int count,
            String before
    ) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/v1/candles")
                                .queryParam("symbol", ticker)
                                .queryParam("interval", "1d")
                                .queryParam("count", count)
                                .queryParam("adjusted", true);
                        if (before != null && !before.isBlank()) {
                            uriBuilder.queryParam("before", before);
                        }
                        return uriBuilder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(CandlesResponse.class);
        } catch (HttpClientErrorException.Unauthorized exception) {
            accessTokenIssuer.invalidate(credentials);
            throw new MarketDataProviderAccessDeniedException(
                    com.tradeguide.domain.market.MarketDataProvider.TOSS_SECURITIES,
                    MarketDataProviderAccessDeniedException.Reason.INVALID_CREDENTIALS,
                    "토스증권 자격 증명이 유효하지 않거나 만료되었습니다.",
                    exception
            );
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new MarketDataProviderAccessDeniedException(
                    com.tradeguide.domain.market.MarketDataProvider.TOSS_SECURITIES,
                    MarketDataProviderAccessDeniedException.Reason.IP_NOT_ALLOWED,
                    "토스증권 오픈API 허용 IP 목록에 현재 서버 IP가 등록되어 있지 않습니다.",
                    exception
            );
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                throw new MarketDataRateLimitExceededException(
                        "토스증권 캔들 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
                        exception
                );
            }
            throw new MarketDataUnavailableException("토스증권 캔들 조회에 실패했습니다.", exception);
        } catch (RestClientException exception) {
            throw new MarketDataUnavailableException("토스증권 캔들 조회에 실패했습니다.", exception);
        }
    }

    private MarketCandle toMarketCandle(CandleItem item, Market market, String ticker) {
        if (item.timestamp() == null || item.openPrice() == null || item.highPrice() == null
                || item.lowPrice() == null || item.closePrice() == null || item.volume() == null) {
            throw new MarketDataUnavailableException("토스증권 캔들 응답이 올바르지 않습니다.");
        }

        if (market == Market.US && item.currency() != null
                && !ACCOUNT_CURRENCY.equalsIgnoreCase(item.currency().trim())) {
            return null;
        }

        try {
            return new MarketCandle(
                    market,
                    ticker,
                    parseDate(item.timestamp()),
                    new BigDecimal(item.openPrice()),
                    new BigDecimal(item.highPrice()),
                    new BigDecimal(item.lowPrice()),
                    new BigDecimal(item.closePrice()),
                    new BigDecimal(item.volume()).longValueExact()
            );
        } catch (RuntimeException exception) {
            throw new MarketDataUnavailableException("토스증권 캔들 데이터 형식이 올바르지 않습니다.", exception);
        }
    }

    private List<MarketCandle> aggregateWeekly(
            List<MarketCandle> dailyCandles,
            Market market,
            String ticker,
            int outputSize
    ) {
        Map<LocalDate, List<MarketCandle>> byWeek = dailyCandles.stream()
                .collect(Collectors.groupingBy(
                        candle -> candle.getTradingDate()
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<MarketCandle> weeklyCandles = byWeek.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> aggregateWeek(entry.getKey(), entry.getValue(), market, ticker))
                .toList();

        if (weeklyCandles.size() < outputSize) {
            throw new MarketDataUnavailableException(
                    "토스증권 캔들 데이터가 전략 계산에 필요한 주봉 개수보다 부족합니다."
            );
        }

        return weeklyCandles.subList(weeklyCandles.size() - outputSize, weeklyCandles.size());
    }

    private MarketCandle aggregateWeek(
            LocalDate weekStart,
            List<MarketCandle> candles,
            Market market,
            String ticker
    ) {
        List<MarketCandle> sorted = candles.stream()
                .sorted(Comparator.comparing(MarketCandle::getTradingDate))
                .toList();
        BigDecimal high = sorted.stream().map(MarketCandle::getHigh).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal low = sorted.stream().map(MarketCandle::getLow).min(BigDecimal::compareTo).orElseThrow();
        long volume = sorted.stream().mapToLong(MarketCandle::getVolume).sum();
        return new MarketCandle(
                market,
                ticker,
                weekStart,
                sorted.get(0).getOpen(),
                high,
                low,
                sorted.get(sorted.size() - 1).getClose(),
                volume
        );
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            try {
                return OffsetDateTime.parse(value).toLocalDate();
            } catch (RuntimeException ignoredOffset) {
                try {
                    return Instant.parse(value).atZone(java.time.ZoneOffset.UTC).toLocalDate();
                } catch (RuntimeException ignoredInstant) {
                    throw new IllegalArgumentException("timestamp=" + value);
                }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CandlesResponse(CandlesResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CandlesResult(List<CandleItem> candles, String nextBefore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CandleItem(
            String timestamp,
            String openPrice,
            String highPrice,
            String lowPrice,
            String closePrice,
            String volume,
            String currency
    ) {}
}
