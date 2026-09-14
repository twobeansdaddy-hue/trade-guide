package com.tradeguide.service.broker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.MarketPrice;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 토스증권의 읽기 전용 다종목 현재가 조회 어댑터다.
 *
 * <p>공식 명세 기준 계약이다. {@code GET /api/v1/prices}는 쉼표로 구분한 심볼 목록을
 * 최대 {@value #MAX_SYMBOLS_PER_REQUEST}개까지 받고, 토큰 기반(client-credentials) 인증을
 * 쓴다. 이 어댑터는 주문·예약 주문·계좌 변경 어떤 것도 수행하지 않는다.
 *
 * <p>{@link com.tradeguide.service.market.MarketPriceProviderRegistry}만 이 클래스를
 * 호출한다. 다른 시장 데이터 제공자(Twelve Data)와 달리 이 제공자는 서버 전역 자격 증명이
 * 아니라 <b>회원별로 검증된 토스증권 연결</b>의 자격 증명이 있어야 호출할 수 있으므로,
 * {@link com.tradeguide.service.market.MarketPriceProvider}를 직접 구현하지 않고
 * {@link BrokerCredentials}를 명시적 인자로 받는다.
 */
@Component
public class TossSecuritiesMarketPriceProvider {

    /** 명세가 정한 요청당 최대 심볼 수다. */
    static final int MAX_SYMBOLS_PER_REQUEST = 200;

    private final RestClient restClient;
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer;

    public TossSecuritiesMarketPriceProvider(
            @Qualifier("brokerRestClientBuilder") RestClient.Builder builder,
            @Value("${toss-securities.base-url:https://openapi.tossinvest.com}") String baseUrl,
            TossSecuritiesAccessTokenIssuer accessTokenIssuer
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.accessTokenIssuer = accessTokenIssuer;
    }

    /**
     * 여러 종목의 현재가를 조회한다. {@value #MAX_SYMBOLS_PER_REQUEST}개를 넘으면 여러
     * 요청으로 나눠 호출하고 결과를 합친다.
     *
     * @return 정상 조회된 종목만 담은 맵. 키는 대문자로 정규화한 티커다.
     */
    public Map<String, MarketPrice> getCurrentPrices(
            BrokerCredentials credentials,
            Market market,
            List<String> tickers
    ) {
        if (tickers == null || tickers.isEmpty()) {
            throw new IllegalArgumentException("조회할 종목이 필요합니다.");
        }

        List<String> normalizedTickers = tickers.stream()
                .map(ticker -> ticker.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        Map<String, MarketPrice> result = new LinkedHashMap<>();
        for (int start = 0; start < normalizedTickers.size(); start += MAX_SYMBOLS_PER_REQUEST) {
            List<String> chunk = normalizedTickers.subList(
                    start, Math.min(start + MAX_SYMBOLS_PER_REQUEST, normalizedTickers.size()));
            result.putAll(callPricesEndpoint(credentials, market, chunk));
        }
        return result;
    }

    private Map<String, MarketPrice> callPricesEndpoint(
            BrokerCredentials credentials,
            Market market,
            List<String> chunk
    ) {
        String accessToken = accessTokenIssuer.issueAccessToken(credentials);
        String symbolsParam = String.join(",", chunk);
        Instant capturedAt = Instant.now();

        PricesResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/prices")
                            .queryParam("symbols", symbolsParam)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(PricesResponse.class);
        } catch (HttpClientErrorException.Unauthorized exception) {
            // 토큰이 이미 무효화된 상태이므로 캐시를 버려 다음 호출이 새로 발급하게 한다.
            accessTokenIssuer.invalidate(credentials);
            throw new MarketDataProviderAccessDeniedException(
                    MarketDataProvider.TOSS_SECURITIES,
                    MarketDataProviderAccessDeniedException.Reason.INVALID_CREDENTIALS,
                    "토스증권 자격 증명이 유효하지 않거나 만료되었습니다. Client ID/Secret을 다시 확인해 주세요.",
                    exception
            );
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new MarketDataProviderAccessDeniedException(
                    MarketDataProvider.TOSS_SECURITIES,
                    MarketDataProviderAccessDeniedException.Reason.IP_NOT_ALLOWED,
                    "토스증권 오픈API 허용 IP 목록에 현재 서버 IP가 등록되어 있지 않습니다."
                            + " 토스증권 개발자 센터에서 서버 IP를 등록한 뒤 다시 시도해 주세요.",
                    exception
            );
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                throw new MarketDataRateLimitExceededException(
                        "토스증권 현재가 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
                        exception
                );
            }
            throw new MarketDataUnavailableException("토스증권 현재가 조회에 실패했습니다.", exception);
        } catch (RestClientException exception) {
            throw new MarketDataUnavailableException("토스증권 현재가 조회에 실패했습니다.", exception);
        }

        if (response == null || response.result() == null) {
            throw new MarketDataUnavailableException("토스증권 현재가 응답이 올바르지 않습니다.");
        }

        String expectedCurrency = expectedCurrency(market);

        Map<String, MarketPrice> result = new LinkedHashMap<>();
        for (PriceItem item : response.result()) {
            if (item == null || item.symbol() == null || item.symbol().isBlank() || item.lastPrice() == null) {
                // 종목 하나의 누락·오류 항목이 나머지 종목 결과를 막지 않는다.
                continue;
            }
            if (expectedCurrency != null && item.currency() != null
                    && !expectedCurrency.equalsIgnoreCase(item.currency().trim())) {
                // 통화가 기대와 다른 종목은 원장 통화를 오염시키지 않도록 조용히 뺀다.
                // Trade Guide 원장은 현재 단일 통화(USD)만 가정하므로, 다른 통화 값을
                // 그 통화인 척 섞지 않는다.
                continue;
            }
            try {
                result.put(
                        item.symbol().trim().toUpperCase(Locale.ROOT),
                        new MarketPrice(
                                market,
                                item.symbol().trim().toUpperCase(Locale.ROOT),
                                new BigDecimal(item.lastPrice()),
                                capturedAt
                        )
                );
            } catch (NumberFormatException ignored) {
                // 가격 형식이 올바르지 않은 종목도 같은 방식으로 그 종목만 결과에서 뺀다.
            }
        }
        return result;
    }

    /**
     * 이 시장에서 기대하는 통화 코드다. {@link Market#KR}은 아직 원화 원장을 지원하지
     * 않으므로 {@code null}을 돌려주고, 그 시장의 응답은 통화로 걸러내지 않는다(다른
     * 계층이 이미 KR 원장 반영을 막는다).
     */
    private String expectedCurrency(Market market) {
        return market == Market.US ? "USD" : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PricesResponse(List<PriceItem> result) {
    }

    /** 명세의 응답 항목에서 이 어댑터가 쓰는 필드만 매핑한다. 가격 필드명은 {@code lastPrice}이고 JSON 문자열 decimal이다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PriceItem(String symbol, String marketCountry, String currency, String lastPrice) {
    }
}
