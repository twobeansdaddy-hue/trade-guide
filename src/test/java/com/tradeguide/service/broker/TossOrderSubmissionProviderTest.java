package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderSide;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static com.tradeguide.domain.broker.BrokerCredentialsFixture.tossCredentials;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * 토스증권 공식 OpenAPI 계약({@code POST /api/v1/orders}, 시장가·수량 기반,
 * {@code X-Tossinvest-Account} 헤더)을 가짜 서버로 검증한다. 실제 토스증권 API를
 * 호출하지 않고, 더미 자격 증명만 사용한다.
 */
class TossOrderSubmissionProviderTest {

    private static final String BASE_URL = "https://openapi.example.test";
    private static final String ORDERS_URL = BASE_URL + "/api/v1/orders";

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer = mock(TossSecuritiesAccessTokenIssuer.class);
    private final TossOrderSubmissionProvider provider =
            new TossOrderSubmissionProvider(restClientBuilder, BASE_URL, accessTokenIssuer);

    @Test
    void submitsMarketOrderWithoutPriceAndReturnsOrderId() {
        givenToken();
        server.expect(requestTo(ORDERS_URL))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token"))
                .andExpect(header("X-Tossinvest-Account", "1"))
                .andExpect(jsonPath("$.symbol").value("SOXL"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.orderType").value("MARKET"))
                .andExpect(jsonPath("$.quantity").value("10"))
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.confirmHighValueOrder").doesNotExist())
                .andRespond(withSuccess("""
                        {"result": {"orderId": "order-123", "clientOrderId": null}}
                        """, MediaType.APPLICATION_JSON));

        var result = provider.submit(
                tossCredentials("id", "secret"),
                new BrokerOrderSubmissionRequest("1", "SOXL", BrokerOrderSide.BUY, new BigDecimal("10")));

        assertThat(result.success()).isTrue();
        assertThat(result.providerOrderId()).isEqualTo("order-123");
        server.verify();
    }

    @Test
    void mapsClientErrorCodeToFailureReason() {
        givenToken();
        server.expect(requestTo(ORDERS_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error": {"requestId": "req-1", "code": "invalid-request",
                                 "message": "호가 유형이 올바르지 않습니다."}}
                                """));

        var result = provider.submit(
                tossCredentials("id", "secret"),
                new BrokerOrderSubmissionRequest("1", "SOXL", BrokerOrderSide.BUY, new BigDecimal("10")));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReasonCode()).isEqualTo("invalid-request");
        server.verify();
    }

    @Test
    void invalidatesCachedTokenOnUnauthorized() {
        givenToken();
        server.expect(requestTo(ORDERS_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        var result = provider.submit(
                tossCredentials("id", "secret"),
                new BrokerOrderSubmissionRequest("1", "SOXL", BrokerOrderSide.BUY, new BigDecimal("10")));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReasonCode()).isEqualTo("UNAUTHORIZED");
        verify(accessTokenIssuer).invalidate(tossCredentials("id", "secret"));
        server.verify();
    }

    @Test
    void treatsMissingOrderIdAsInvalidResponse() {
        givenToken();
        server.expect(requestTo(ORDERS_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        var result = provider.submit(
                tossCredentials("id", "secret"),
                new BrokerOrderSubmissionRequest("1", "SOXL", BrokerOrderSide.BUY, new BigDecimal("10")));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReasonCode()).isEqualTo("INVALID_RESPONSE");
        server.verify();
    }

    @Test
    void doesNotCallProviderTwiceOrThrowOnServerError() {
        givenToken();
        server.expect(requestTo(ORDERS_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        var result = provider.submit(
                tossCredentials("id", "secret"),
                new BrokerOrderSubmissionRequest("1", "SOXL", BrokerOrderSide.BUY, new BigDecimal("10")));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReasonCode()).isEqualTo("SUBMISSION_FAILED");
        server.verify();
    }

    @Test
    void rejectsNullRequestWithoutCallingBroker() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> provider.submit(tossCredentials("id", "secret"), null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(accessTokenIssuer);
    }

    private void givenToken() {
        when(accessTokenIssuer.issueAccessToken(tossCredentials("id", "secret"))).thenReturn("test-access-token");
    }
}
