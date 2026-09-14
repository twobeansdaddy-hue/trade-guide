package com.tradeguide.service.market;

import com.tradeguide.config.BrokerCredentialKeyringProperties;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataProviderNotConfiguredException;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.service.broker.AesGcmBrokerCredentialCipher;
import com.tradeguide.service.broker.BrokerCredentialCipher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwelveDataMarketPriceProviderTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TwelveDataMarketPriceProvider provider =
            new TwelveDataMarketPriceProvider(
                    restClientBuilder,
                    "test-api-key",
                    new MarketPriceCache(),
                    new MarketDataProviderConfigurationStatus("test-api-key", unconfiguredBrokerCipher()),
                    new TwelveDataRateLimitTracker(Clock.systemUTC(), 30)
            );

    private static BrokerCredentialCipher unconfiguredBrokerCipher() {
        return new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(null, null, null)
        );
    }

    @Test
    void throwsExceptionWhenTwelveDataRateLimitIsExceeded() {
        server.expect(
                        requestTo(
                                "https://api.twelvedata.com/price?symbol=AAPL"
                        )
                )
                .andExpect(
                        header(HttpHeaders.AUTHORIZATION, "apikey test-api-key")
                )
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                        {
                                          "code": 429,
                                          "message": "Too many requests",
                                          "status": "error"
                                        }
                                        """)
                );

        assertThatThrownBy(() ->
                provider.getCurrentPrice(Market.US, "AAPL")
        )
                .isInstanceOf(MarketDataRateLimitExceededException.class)
                .hasMessage("현재가 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.");

        server.verify();
    }

    @Test
    void throwsProviderNotConfiguredExceptionWithoutCallingProviderWhenApiKeyIsBlank() {
        RestClient.Builder builderWithoutApiKey = RestClient.builder();
        MockRestServiceServer unusedServer =
                MockRestServiceServer.bindTo(builderWithoutApiKey).build();

        TwelveDataMarketPriceProvider providerWithoutApiKey =
                new TwelveDataMarketPriceProvider(
                        builderWithoutApiKey,
                        "",
                        new MarketPriceCache(),
                        new MarketDataProviderConfigurationStatus("", unconfiguredBrokerCipher()),
                        new TwelveDataRateLimitTracker(Clock.systemUTC(), 30)
                );

        assertThatExceptionOfType(MarketDataProviderNotConfiguredException.class)
                .isThrownBy(() ->
                        providerWithoutApiKey.getCurrentPrice(Market.US, "AAPL")
                )
                .satisfies(exception -> assertThat(exception.getProvider())
                        .isEqualTo(MarketDataProvider.TWELVE_DATA));

        unusedServer.verify();
    }

    @Test
    void fetchesMultipleSymbolsInOneBatchRequest() {
        server.expect(
                        requestTo("https://api.twelvedata.com/price?symbol=AAPL,MSFT")
                )
                .andExpect(header(HttpHeaders.AUTHORIZATION, "apikey test-api-key"))
                .andRespond(withSuccess(
                        """
                        {
                          "AAPL": { "price": "210.50" },
                          "MSFT": { "price": "400.10" }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        Map<String, MarketPrice> prices =
                provider.getCurrentPrices(Market.US, List.of("AAPL", "MSFT"));

        assertThat(prices).hasSize(2);
        assertThat(prices.get("AAPL").getCurrentPrice()).isEqualByComparingTo("210.50");
        assertThat(prices.get("MSFT").getCurrentPrice()).isEqualByComparingTo("400.10");
        server.verify();
    }

    @Test
    void omitsSymbolMissingFromBatchResponseWithoutFailingOthers() {
        server.expect(
                        requestTo("https://api.twelvedata.com/price?symbol=AAPL,BADSYM")
                )
                .andRespond(withSuccess(
                        """
                        {
                          "AAPL": { "price": "210.50" },
                          "BADSYM": { "code": 400, "message": "symbol not found", "status": "error" }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        Map<String, MarketPrice> prices =
                provider.getCurrentPrices(Market.US, List.of("AAPL", "BADSYM"));

        assertThat(prices).containsOnlyKeys("AAPL");
        server.verify();
    }

    @Test
    void appliesGlobalCooldownAfterRateLimitAndBlocksWithoutCallingProviderAgain() {
        TwelveDataMarketPriceProvider cooldownProvider = new TwelveDataMarketPriceProvider(
                restClientBuilder,
                "test-api-key",
                new MarketPriceCache(),
                new MarketDataProviderConfigurationStatus("test-api-key", unconfiguredBrokerCipher()),
                new TwelveDataRateLimitTracker(Clock.systemUTC(), 60)
        );

        server.expect(requestTo("https://api.twelvedata.com/price?symbol=AAPL"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "code": 429, "message": "Too many requests", "status": "error" }
                                """));

        assertThatThrownBy(() -> cooldownProvider.getCurrentPrice(Market.US, "AAPL"))
                .isInstanceOf(MarketDataRateLimitExceededException.class);

        // 쿨다운 중에는 심볼이 달라도(MSFT) 외부 호출 없이 즉시 거부해야 한다.
        // MockRestServiceServer가 두 번째 기대를 등록하지 않았으므로, 실제로 호출되면
        // 여기서 예외를 던져 검증한다.
        assertThatThrownBy(() -> cooldownProvider.getCurrentPrice(Market.US, "MSFT"))
                .isInstanceOf(MarketDataRateLimitExceededException.class);

        server.verify();
    }
}
