package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketPriceUnavailableException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Service
public class TwelveDataMarketPriceProvider implements MarketPriceProvider{

    private final RestClient restClient;
    private final String apiKey;

    public TwelveDataMarketPriceProvider(
            RestClient.Builder restClientBuilder,
            @Value("${twelve-data.api-key}") String apiKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.twelvedata.com")
                .build();
        this.apiKey = apiKey;
    }

    @Override
    public MarketPrice getCurrentPrice(Market market, String ticker) {
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
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(TwelveDataPriceResponse.class);
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
