package com.tradeguide.service.market;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketPriceRateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class TwelveDataMarketPriceProviderTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TwelveDataMarketPriceProvider provider =
            new TwelveDataMarketPriceProvider(
                    restClientBuilder,
                    "test-api-key",
                    new MarketPriceCache()
            );

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
                .isInstanceOf(MarketPriceRateLimitExceededException.class)
                .hasMessage("현재가 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.");

        server.verify();
    }
}
