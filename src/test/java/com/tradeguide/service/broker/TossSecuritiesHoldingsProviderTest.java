package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스증권 공식 OpenAPI 계약({@code GET /api/v1/holdings}, {@code X-Tossinvest-Account} 헤더,
 * {@code result}가 {@code items}를 담은 개요 객체)을 가짜 서버로 검증한다.
 * 실제 토스증권 API는 호출하지 않는다.
 */
class TossSecuritiesHoldingsProviderTest {

    private static final String HOLDINGS_URL = "https://openapi.example.test/api/v1/holdings";

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer =
            mock(TossSecuritiesAccessTokenIssuer.class);
    private final TossSecuritiesHoldingsProvider provider = new TossSecuritiesHoldingsProvider(
            restClientBuilder,
            "https://openapi.example.test",
            accessTokenIssuer
    );

    @Test
    void readsHoldingsFromOverviewResultUsingAccountHeader() {
        when(accessTokenIssuer.issueAccessToken("id", "secret")).thenReturn("test-access-token");
        server.expect(requestTo(HOLDINGS_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token"))
                .andExpect(header("X-Tossinvest-Account", "1"))
                .andRespond(withSuccess(overviewWithItems("""
                        {
                          "symbol": "005930",
                          "name": "삼성전자",
                          "marketCountry": "KR",
                          "currency": "KRW",
                          "quantity": "100",
                          "lastPrice": "72000",
                          "averagePurchasePrice": "65000",
                          "marketValue": {"amount": "7200000", "amountAfterCost": "7050000"},
                          "profitLoss": {"amount": "700000", "rate": "0.1077"},
                          "dailyProfitLoss": {"amount": "100000", "rate": "0.0141"},
                          "cost": {"amount": "150000"}
                        },
                        {
                          "symbol": "aapl",
                          "name": "Apple",
                          "marketCountry": "US",
                          "currency": "USD",
                          "quantity": "7",
                          "lastPrice": "255.00",
                          "averagePurchasePrice": "221.86",
                          "marketValue": {"amount": "1785", "amountAfterCost": "1771.43"},
                          "profitLoss": {"amount": "232", "rate": "0.1494"},
                          "dailyProfitLoss": {"amount": "25", "rate": "0.0142"},
                          "cost": {"amount": "13.57"}
                        }
                        """), MediaType.APPLICATION_JSON));

        BrokerHoldingSnapshot snapshot = provider.fetchHoldings("id", "secret", "1");

        assertThat(snapshot.unsupportedMarketCount()).isZero();
        assertThat(snapshot.holdings()).containsExactly(
                new BrokerHolding(Market.KR, "005930", new BigDecimal("100"), new BigDecimal("65000")),
                new BrokerHolding(Market.US, "AAPL", new BigDecimal("7"), new BigDecimal("221.86"))
        );
        server.verify();
    }

    @Test
    void countsUnknownMarketCountryAsUnsupportedInsteadOfFailing() {
        when(accessTokenIssuer.issueAccessToken("id", "secret")).thenReturn("test-access-token");
        server.expect(requestTo(HOLDINGS_URL))
                .andRespond(withSuccess(overviewWithItems("""
                        {
                          "symbol": "SOXL",
                          "marketCountry": "US",
                          "currency": "USD",
                          "quantity": "30",
                          "averagePurchasePrice": "20.5"
                        },
                        {
                          "symbol": "7203",
                          "marketCountry": "JP",
                          "currency": "JPY",
                          "quantity": "5",
                          "averagePurchasePrice": "2500"
                        }
                        """), MediaType.APPLICATION_JSON));

        BrokerHoldingSnapshot snapshot = provider.fetchHoldings("id", "secret", "1");

        assertThat(snapshot.unsupportedMarketCount()).isEqualTo(1);
        assertThat(snapshot.holdings()).containsExactly(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.5"))
        );
        server.verify();
    }

    @Test
    void readsEmptyHoldingsWhenAccountHasNoItems() {
        when(accessTokenIssuer.issueAccessToken("id", "secret")).thenReturn("test-access-token");
        server.expect(requestTo(HOLDINGS_URL))
                .andRespond(withSuccess("""
                        {
                          "result": {
                            "totalPurchaseAmount": {"krw": "0", "usd": "0"},
                            "marketValue": {"amount": {"krw": "0", "usd": "0"}},
                            "profitLoss": {"amount": {"krw": "0", "usd": "0"}, "rate": "0"},
                            "dailyProfitLoss": {"amount": {"krw": "0", "usd": "0"}, "rate": "0"},
                            "items": []
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        BrokerHoldingSnapshot snapshot = provider.fetchHoldings("id", "secret", "1");

        assertThat(snapshot.holdings()).isEmpty();
        assertThat(snapshot.unsupportedMarketCount()).isZero();
        server.verify();
    }

    @Test
    void failsWhenResultIsNotAHoldingsOverview() {
        when(accessTokenIssuer.issueAccessToken("id", "secret")).thenReturn("test-access-token");
        server.expect(requestTo(HOLDINGS_URL))
                .andRespond(withSuccess("""
                        {"result": {"totalPurchaseAmount": {"krw": "0", "usd": "0"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetchHoldings("id", "secret", "1"))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("토스증권 보유 종목 응답이 올바르지 않습니다.");

        server.verify();
    }

    @Test
    void failsWhenDecimalStringFieldIsNotUsable() {
        when(accessTokenIssuer.issueAccessToken("id", "secret")).thenReturn("test-access-token");
        server.expect(requestTo(HOLDINGS_URL))
                .andRespond(withSuccess(overviewWithItems("""
                        {
                          "symbol": "SOXL",
                          "marketCountry": "US",
                          "currency": "USD",
                          "quantity": "삼십",
                          "averagePurchasePrice": "20.5"
                        }
                        """), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetchHoldings("id", "secret", "1"))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("토스증권 보유 종목 응답이 올바르지 않습니다.");

        server.verify();
    }

    @Test
    void failsWhenBrokerResponseIsNotUsable() {
        when(accessTokenIssuer.issueAccessToken("id", "secret")).thenReturn("test-access-token");
        server.expect(requestTo(HOLDINGS_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> provider.fetchHoldings("id", "secret", "1"))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("토스증권 보유 종목 조회에 실패했습니다.");

        server.verify();
    }

    @Test
    void rejectsMissingAccountSequenceBeforeCallingBroker() {
        assertThatThrownBy(() -> provider.fetchHoldings("id", "secret", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("증권사 계좌 일련번호가 필요합니다.");

        verifyNoInteractions(accessTokenIssuer);
        server.verify();
    }

    /** 명세의 {@code HoldingsOverview} 요약 필드를 그대로 둔 채 {@code items}만 바꾼 응답을 만든다. */
    private String overviewWithItems(String itemsJson) {
        return """
                {
                  "result": {
                    "totalPurchaseAmount": {"krw": "6500000", "usd": "1553"},
                    "marketValue": {
                      "amount": {"krw": "7200000", "usd": "1785"},
                      "amountAfterCost": {"krw": "7050000", "usd": "1771.43"}
                    },
                    "profitLoss": {
                      "amount": {"krw": "700000", "usd": "232"},
                      "amountAfterCost": {"krw": "550000", "usd": "218.43"},
                      "rate": "0.1179",
                      "rateAfterCost": "0.0983"
                    },
                    "dailyProfitLoss": {"amount": {"krw": "100000", "usd": "25"}, "rate": "0.0141"},
                    "items": [%s]
                  }
                }
                """.formatted(itemsJson);
    }
}
