package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketPriceUnavailableException;
import com.tradeguide.exception.MarketPriceRateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Service
public class TwelveDataMarketPriceProvider implements MarketPriceProvider {

    private final RestClient restClient;
    private final String apiKey;
    private final MarketPriceCache marketPriceCache;

    public TwelveDataMarketPriceProvider(
            RestClient.Builder restClientBuilder,
            @Value("${twelve-data.api-key}") String apiKey,
            MarketPriceCache marketPriceCache
    ) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.twelvedata.com")
                .build();
        this.apiKey = apiKey;
        this.marketPriceCache = marketPriceCache;
    }

    @Override
    public MarketPrice getCurrentPrice(Market market, String ticker) {
        return marketPriceCache.getOrLoad(
                market,
                ticker,
                () -> loadCurrentPrice(market, ticker)
        );
    }

    private MarketPrice loadCurrentPrice(
            Market market,
            String ticker
    ) {
        if (apiKey.isBlank()) {
            throw new MarketPriceUnavailableException(
                    "현재가 조회 API 키가 설정되지 않았습니다."
            );
        }

        String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
        TwelveDataPriceResponse response;

        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/price")
                            .queryParam("symbol", normalizedTicker)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "apikey " + apiKey)
                    .retrieve()
                    .body(TwelveDataPriceResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode()
                    .isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                throw new MarketPriceRateLimitExceededException(
                        "현재가 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
                        exception
                );
            }

            throw new MarketPriceUnavailableException(
                    "현재가 조회에 실패했습니다.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new MarketPriceUnavailableException(
                    "현재가 조회에 실패했습니다.",
                    exception
            );
        }

        if (response == null || response.price() == null) {
            throw new MarketPriceUnavailableException(
                    "현재가 응답이 올바르지 않습니다."
            );
        }

        try {
            return new MarketPrice(
                    market,
                    normalizedTicker,
                    new BigDecimal(response.price()),
                    Instant.now()
            );
        } catch (NumberFormatException exception) {
            throw new MarketPriceUnavailableException(
                    "현재가 응답의 가격 형식이 올바르지 않습니다.",
                    exception
            );
        }
    }

    private record TwelveDataPriceResponse(String price) {
    }
}
