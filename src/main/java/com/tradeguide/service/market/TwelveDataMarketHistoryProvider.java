package com.tradeguide.service.market;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class TwelveDataMarketHistoryProvider implements MarketHistoryProvider {

    private final RestClient restClient;
    private final String apiKey;

    public TwelveDataMarketHistoryProvider(
            RestClient.Builder restClientBuilder,
            @Value("${twelve-data.api-key}") String apiKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.twelvedata.com")
                .build();
        this.apiKey = apiKey;
    }

    @Override
    public List<MarketCandle> getCandles(
            Market market,
            String ticker,
            CandleInterval interval,
            int outputSize
    ) {
        if (outputSize < 1 || outputSize > 5000) {
            throw new IllegalArgumentException(
                    "캔들 조회 개수는 1에서 5000 사이여야 합니다."
            );
        }

        if (apiKey.isBlank()) {
            throw new MarketDataUnavailableException(
                    "시장 데이터 조회 API 키가 설정되지 않았습니다."
            );
        }

        String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
        TwelveDataTimeSeriesResponse response;

        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/time_series")
                            .queryParam("symbol", normalizedTicker)
                            .queryParam("interval", toTwelveDataInterval(interval))
                            .queryParam("outputsize", outputSize)
                            .queryParam("order","asc")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "apikey " + apiKey)
                    .retrieve()
                    .body(TwelveDataTimeSeriesResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode()
                    .isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                throw new MarketDataRateLimitExceededException(
                        "시장 데이터 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
                        exception
                );
            }

            throw new MarketDataUnavailableException(
                    "시장 데이터 조회에 실패했습니다.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new MarketDataUnavailableException(
                    "시장 데이터 조회에 실패했습니다.",
                    exception
            );
        }

        if (response == null
                || response.values() == null
                || response.values().isEmpty()) {
            throw new MarketDataUnavailableException(
                    "캔들 데이터를 찾을 수 없습니다."
            );
        }

        try {
            return response.values().stream()
                    .map(candle -> new MarketCandle(
                            market,
                            normalizedTicker,
                            LocalDate.parse(candle.datetime()),
                            new BigDecimal(candle.open()),
                            new BigDecimal(candle.high()),
                            new BigDecimal(candle.low()),
                            new BigDecimal(candle.close()),
                            Long.parseLong(candle.volume())
                    ))
                    .toList();
        } catch (RuntimeException exception) {
            throw new MarketDataUnavailableException(
                    "캔들 데이터 형식이 올바르지 않습니다.",
                    exception
            );
        }

    }

    private record TwelveDataTimeSeriesResponse(
            List<TwelveDataCandleResponse> values
    ) {
    }

    private record TwelveDataCandleResponse(
            String datetime,
            String open,
            String high,
            String low,
            String close,
            String volume
    ) {
    }

    private String toTwelveDataInterval(
            CandleInterval interval
    ) {
        return switch (interval) {
            case DAILY -> "1day";
            case WEEKLY -> "1week";
        };
    }
}
