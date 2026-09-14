package com.tradeguide.service.broker;

import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataProviderAccessDeniedException;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tradeguide.domain.broker.BrokerCredentialsFixture.tossCredentials;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스증권 공식 다종목 현재가 계약({@code GET /api/v1/prices}, 최대 200심볼, 토큰 기반 인증)을
 * 가짜 서버로 검증한다. 실제 토스증권 API는 호출하지 않는다.
 */
class TossSecuritiesMarketPriceProviderTest {

    private static final String PRICES_URL_PREFIX = "https://openapi.example.test/api/v1/prices";

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer =
            mock(TossSecuritiesAccessTokenIssuer.class);
    private final TossSecuritiesMarketPriceProvider provider = new TossSecuritiesMarketPriceProvider(
            restClientBuilder,
            "https://openapi.example.test",
            accessTokenIssuer
    );

    @Test
    void readsMultipleSymbolsUsingBearerToken() {
        when(accessTokenIssuer.issueAccessToken(tossCredentials("id", "secret")))
                .thenReturn("test-access-token");
        server.expect(requestTo(PRICES_URL_PREFIX + "?symbols=AAPL,MSFT"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token"))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {"symbol": "AAPL", "timestamp": "2026-09-10T13:30:00Z", "marketCountry": "US", "currency": "USD", "lastPrice": "210.50"},
                            {"symbol": "MSFT", "timestamp": "2026-09-10T13:30:00Z", "marketCountry": "US", "currency": "USD", "lastPrice": "400.10"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, MarketPrice> prices = provider.getCurrentPrices(
                tossCredentials("id", "secret"), Market.US, List.of("AAPL", "MSFT")
        );

        assertThat(prices.get("AAPL").getCurrentPrice()).isEqualByComparingTo("210.50");
        assertThat(prices.get("MSFT").getCurrentPrice()).isEqualByComparingTo("400.10");
        server.verify();
    }

    @Test
    void omitsSymbolWithMismatchedCurrencyToAvoidMixingLedgerCurrency() {
        when(accessTokenIssuer.issueAccessToken(tossCredentials("id", "secret")))
                .thenReturn("test-access-token");
        server.expect(requestTo(PRICES_URL_PREFIX + "?symbols=AAPL,005930"))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {"symbol": "AAPL", "timestamp": "2026-09-10T13:30:00Z", "marketCountry": "US", "currency": "USD", "lastPrice": "210.50"},
                            {"symbol": "005930", "timestamp": "2026-09-10T13:30:00Z", "marketCountry": "KR", "currency": "KRW", "lastPrice": "72000"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, MarketPrice> prices = provider.getCurrentPrices(
                tossCredentials("id", "secret"), Market.US, List.of("AAPL", "005930")
        );

        assertThat(prices).containsOnlyKeys("AAPL");
        server.verify();
    }

    @Test
    void omitsSymbolMissingFromResponseWithoutFailingOthers() {
        when(accessTokenIssuer.issueAccessToken(tossCredentials("id", "secret")))
                .thenReturn("test-access-token");
        server.expect(requestTo(PRICES_URL_PREFIX + "?symbols=AAPL,BADSYM"))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {"symbol": "AAPL", "timestamp": "2026-09-10T13:30:00Z", "marketCountry": "US", "currency": "USD", "lastPrice": "210.50"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, MarketPrice> prices = provider.getCurrentPrices(
                tossCredentials("id", "secret"), Market.US, List.of("AAPL", "BADSYM")
        );

        assertThat(prices).containsOnlyKeys("AAPL");
        server.verify();
    }

    @Test
    void splitsRequestsLargerThanTwoHundredSymbolsIntoMultipleChunks() {
        List<String> symbols = IntStream.range(0, 201)
                .mapToObj(i -> "SYM" + i)
                .toList();
        String firstChunkSymbols = String.join(",", symbols.subList(0, 200));
        String secondChunkSymbols = symbols.get(200);

        when(accessTokenIssuer.issueAccessToken(tossCredentials("id", "secret")))
                .thenReturn("test-access-token");
        server.expect(requestTo(PRICES_URL_PREFIX + "?symbols=" + firstChunkSymbols))
                .andRespond(withSuccess(pricesResultJson(symbols.subList(0, 200)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(PRICES_URL_PREFIX + "?symbols=" + secondChunkSymbols))
                .andRespond(withSuccess(pricesResultJson(List.of(secondChunkSymbols)), MediaType.APPLICATION_JSON));

        Map<String, MarketPrice> prices = provider.getCurrentPrices(
                tossCredentials("id", "secret"), Market.US, symbols
        );

        assertThat(prices).hasSize(201);
        server.verify();
    }

    @Test
    void throwsAccessDeniedWithInvalidCredentialsReasonAndInvalidatesTokenOnUnauthorized() {
        when(accessTokenIssuer.issueAccessToken(tossCredentials("id", "secret")))
                .thenReturn("test-access-token");
        server.expect(requestTo(PRICES_URL_PREFIX + "?symbols=AAPL"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.getCurrentPrices(
                tossCredentials("id", "secret"), Market.US, List.of("AAPL")
        ))
                .isInstanceOf(MarketDataProviderAccessDeniedException.class)
                .satisfies(exception -> assertThat(
                        ((MarketDataProviderAccessDeniedException) exception).getReason())
                        .isEqualTo(MarketDataProviderAccessDeniedException.Reason.INVALID_CREDENTIALS));

        verify(accessTokenIssuer).invalidate(tossCredentials("id", "secret"));
        server.verify();
    }

    @Test
    void throwsAccessDeniedWithIpNotAllowedReasonOnForbidden() {
        when(accessTokenIssuer.issueAccessToken(tossCredentials("id", "secret")))
                .thenReturn("test-access-token");
        server.expect(requestTo(PRICES_URL_PREFIX + "?symbols=AAPL"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> provider.getCurrentPrices(
                tossCredentials("id", "secret"), Market.US, List.of("AAPL")
        ))
                .isInstanceOf(MarketDataProviderAccessDeniedException.class)
                .satisfies(exception -> assertThat(
                        ((MarketDataProviderAccessDeniedException) exception).getReason())
                        .isEqualTo(MarketDataProviderAccessDeniedException.Reason.IP_NOT_ALLOWED));

        server.verify();
    }

    @Test
    void throwsRateLimitExceededOnTooManyRequests() {
        when(accessTokenIssuer.issueAccessToken(tossCredentials("id", "secret")))
                .thenReturn("test-access-token");
        server.expect(requestTo(PRICES_URL_PREFIX + "?symbols=AAPL"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.getCurrentPrices(
                tossCredentials("id", "secret"), Market.US, List.of("AAPL")
        ))
                .isInstanceOf(MarketDataRateLimitExceededException.class);

        server.verify();
    }

    @Test
    void throwsUnavailableWhenResponseIsNotUsable() {
        when(accessTokenIssuer.issueAccessToken(tossCredentials("id", "secret")))
                .thenReturn("test-access-token");
        server.expect(requestTo(PRICES_URL_PREFIX + "?symbols=AAPL"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> provider.getCurrentPrices(
                tossCredentials("id", "secret"), Market.US, List.of("AAPL")
        ))
                .isInstanceOf(MarketDataUnavailableException.class);

        server.verify();
    }

    @Test
    void rejectsEmptyTickerListBeforeCallingBroker() {
        assertThatThrownBy(() -> provider.getCurrentPrices(
                tossCredentials("id", "secret"), Market.US, List.of()
        ))
                .isInstanceOf(IllegalArgumentException.class);

        server.verify();
    }

    private String pricesResultJson(List<String> symbols) {
        String items = symbols.stream()
                .map(symbol -> """
                        {"symbol": "%s", "timestamp": "2026-09-10T13:30:00Z", "marketCountry": "US", "currency": "USD", "lastPrice": "100.00"}
                        """.formatted(symbol))
                .collect(Collectors.joining(","));
        return "{\"result\": [" + items + "]}";
    }
}
