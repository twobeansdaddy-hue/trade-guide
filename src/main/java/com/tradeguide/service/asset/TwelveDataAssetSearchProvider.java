package com.tradeguide.service.asset;

import com.tradeguide.domain.asset.AssetSearchResult;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Locale;

@Service
public class TwelveDataAssetSearchProvider implements AssetSearchProvider {

    private final RestClient restClient;
    private final String apiKey;

    public TwelveDataAssetSearchProvider(
            RestClient.Builder restClientBuilder,
            @Value("${twelve-data.api-key}") String apiKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.twelvedata.com")
                .build();
        this.apiKey = apiKey;
    }

    @Override
    public List<AssetSearchResult> search(Market market, String query, int limit) {
        if (market != Market.US || apiKey.isBlank()) {
            return List.of();
        }

        TwelveDataSymbolSearchResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/symbol_search")
                            .queryParam("symbol", query)
                            .queryParam("outputsize", limit)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "apikey " + apiKey)
                    .retrieve()
                    .body(TwelveDataSymbolSearchResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                throw new MarketDataRateLimitExceededException(
                        "종목 검색 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
                        exception
                );
            }
            throw new MarketDataUnavailableException("외부 종목 검색에 실패했습니다.", exception);
        } catch (RestClientException exception) {
            throw new MarketDataUnavailableException("외부 종목 검색에 실패했습니다.", exception);
        }

        if (response == null || response.data() == null) {
            throw new MarketDataUnavailableException("외부 종목 검색 응답이 올바르지 않습니다.");
        }

        return response.data().stream()
                .filter(result -> "United States".equalsIgnoreCase(result.country()))
                .filter(result -> result.symbol() != null && result.instrument_name() != null)
                .map(result -> new AssetSearchResult(
                        Market.US,
                        result.symbol().toUpperCase(Locale.ROOT),
                        result.instrument_name()
                ))
                .toList();
    }

    private record TwelveDataSymbolSearchResponse(List<TwelveDataSymbolSearchItem> data) {
    }

    private record TwelveDataSymbolSearchItem(
            String symbol,
            String instrument_name,
            String country
    ) {
    }
}
