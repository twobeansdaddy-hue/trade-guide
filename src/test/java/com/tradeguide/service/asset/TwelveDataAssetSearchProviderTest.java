package com.tradeguide.service.asset;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwelveDataAssetSearchProviderTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TwelveDataAssetSearchProvider provider = new TwelveDataAssetSearchProvider(restClientBuilder, "test-api-key");

    @Test
    void returnsOnlyUnitedStatesResults() {
        server.expect(requestTo("https://api.twelvedata.com/symbol_search?symbol=app&outputsize=10"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "apikey test-api-key"))
                .andRespond(withSuccess("""
                        {"data":[
                          {"symbol":"AAPL","instrument_name":"Apple Inc","country":"United States"},
                          {"symbol":"APC","instrument_name":"Apple Corp","country":"Canada"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.search(Market.US, "app", 10))
                .extracting(result -> result.ticker())
                .containsExactly("AAPL");
        server.verify();
    }

    @Test
    void throwsRateLimitException() {
        server.expect(requestTo("https://api.twelvedata.com/symbol_search?symbol=app&outputsize=10"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.search(Market.US, "app", 10))
                .isInstanceOf(MarketDataRateLimitExceededException.class);
        server.verify();
    }
}
