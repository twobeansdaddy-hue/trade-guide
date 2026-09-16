package com.tradeguide.service.broker;

import static com.tradeguide.domain.broker.BrokerCredentialsFixture.tossCredentials;
import com.tradeguide.domain.broker.BrokerOrderHistoryPage;
import com.tradeguide.domain.broker.BrokerOrderHistoryQuery;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderStatusGroup;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스증권 공식 OpenAPI 계약({@code GET /api/v1/orders}, 필수 {@code status} 그룹,
 * {@code X-Tossinvest-Account} 헤더, 커서 페이지 응답)을 가짜 서버로 검증한다.
 * 실제 토스증권 API를 호출하지 않고, 자격 증명도 사용하지 않는다.
 */
class TossSecuritiesOrderHistoryProviderTest {

    private static final String BASE_URL = "https://openapi.example.test";
    private static final String ORDERS_URL = BASE_URL + "/api/v1/orders";
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 8);

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer =
            mock(TossSecuritiesAccessTokenIssuer.class);
    private final TossSecuritiesOrderHistoryProvider provider = new TossSecuritiesOrderHistoryProvider(
            restClientBuilder,
            BASE_URL,
            accessTokenIssuer
    );

    @Test
    void readsClosedOrdersWithAccountHeaderCursorAndPageSize() {
        givenToken();
        server.expect(requestTo(ORDERS_URL + "?status=CLOSED&from=2026-09-01&to=2026-09-08"
                        + "&cursor=eyJvcmRlcmVkQXQiOjF9&limit=50"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token"))
                .andExpect(header("X-Tossinvest-Account", "1"))
                .andRespond(withSuccess("""
                        {
                          "result": {
                            "orders": [
                              {
                                "orderId": "a1b2c3",
                                "symbol": "aapl",
                                "side": "BUY",
                                "orderType": "LIMIT",
                                "timeInForce": "DAY",
                                "status": "FILLED",
                                "price": "220.00",
                                "quantity": "10",
                                "orderAmount": null,
                                "currency": "USD",
                                "orderedAt": "2026-09-02T23:35:00+09:00",
                                "canceledAt": null,
                                "execution": {
                                  "filledQuantity": "10",
                                  "averageFilledPrice": "221.8650",
                                  "filledAmount": "2218.65",
                                  "commission": "1.11",
                                  "tax": "0.05",
                                  "filledAt": "2026-09-03T00:12:31+09:00",
                                  "settlementDate": "2026-09-05"
                                }
                              }
                            ],
                            "nextCursor": "eyJvcmRlcmVkQXQiOjJ9",
                            "hasNext": true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        BrokerOrderHistoryPage page = provider.fetchOrders(tossCredentials("id", "secret"), "1",
                new BrokerOrderHistoryQuery(
                        BrokerOrderStatusGroup.CLOSED, FROM, TO, "eyJvcmRlcmVkQXQiOjF9", 50));

        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("eyJvcmRlcmVkQXQiOjJ9");
        assertThat(page.exclusions().fetchedCount()).isEqualTo(1);
        assertThat(page.exclusions().excludedCount()).isZero();
        assertThat(page.exclusions().unknownEnumCount()).isZero();

        BrokerOrderRecord record = page.records().getFirst();
        assertThat(record.externalOrderId()).isEqualTo("a1b2c3");
        assertThat(record.market()).isEqualTo(Market.US);
        assertThat(record.ticker()).isEqualTo("AAPL");
        assertThat(record.side()).isEqualTo(BrokerOrderSide.BUY);
        assertThat(record.lifecycle()).isEqualTo(BrokerOrderLifecycle.TERMINAL_WITH_FILL);
        assertThat(record.providerStatusCode()).isEqualTo("FILLED");
        assertThat(record.providerOrderType()).isEqualTo("LIMIT");
        assertThat(record.providerTimeInForce()).isEqualTo("DAY");
        assertThat(record.currencyCode()).isEqualTo("USD");
        assertThat(record.orderedQuantity()).isEqualTo(new BigDecimal("10"));
        assertThat(record.filledQuantity()).isEqualTo(new BigDecimal("10"));
        // 문자열 decimal의 스케일을 반올림하거나 정규화하지 않고 그대로 보존한다.
        assertThat(record.averageFilledPrice()).isEqualTo(new BigDecimal("221.8650"));
        assertThat(record.filledAmount()).isEqualTo(new BigDecimal("2218.65"));
        assertThat(record.commission()).isEqualTo(new BigDecimal("1.11"));
        assertThat(record.tax()).isEqualTo(new BigDecimal("0.05"));
        assertThat(record.orderedAt()).isEqualTo(Instant.parse("2026-09-02T14:35:00Z"));
        assertThat(record.filledAt()).isEqualTo(Instant.parse("2026-09-02T15:12:31Z"));
        assertThat(record.settlementDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(record.isLedgerEligible()).isTrue();
        server.verify();
    }

    /** 진행 중 주문은 전량 반환되고 커서·페이지 크기가 무시되므로 보내지도 믿지도 않는다. */
    @Test
    void omitsCursorAndPageSizeForOpenGroupAndDoesNotTrustReturnedCursor() {
        givenToken();
        server.expect(requestTo(ORDERS_URL + "?status=OPEN&from=2026-09-01&to=2026-09-08"))
                .andRespond(withSuccess(singleOrder("PENDING", "0", null, null), MediaType.APPLICATION_JSON)
                );

        BrokerOrderHistoryPage page = provider.fetchOrders(
                tossCredentials("id", "secret"), "1", BrokerOrderHistoryQuery.open(FROM, TO));

        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().getFirst().lifecycle()).isEqualTo(BrokerOrderLifecycle.NOT_FILLED);
        server.verify();
    }

    @Test
    void doesNotReportNextPageWhenCursorIsMissing() {
        givenToken();
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withSuccess("""
                        {"result": {"orders": [], "nextCursor": null, "hasNext": true}}
                        """, MediaType.APPLICATION_JSON));

        BrokerOrderHistoryPage page = provider.fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage());

        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.records()).isEmpty();
        assertThat(page.exclusions().fetchedCount()).isZero();
        server.verify();
    }

    /**
     * 명세의 주문 상태 10종을 체결분 유무와 함께 전수 검증한다.
     * 특히 취소·거부·정정된 주문이라도 체결분이 있으면 실재하는 거래이므로 버리지 않는다.
     */
    @ParameterizedTest(name = "{0} + filledQuantity={1} -> {2}")
    @CsvSource({
            "PENDING,0,NOT_FILLED",
            "PENDING,3,PARTIALLY_FILLED_OPEN",
            "PENDING_CANCEL,0,NOT_FILLED",
            "PENDING_CANCEL,3,PARTIALLY_FILLED_OPEN",
            "PENDING_REPLACE,0,NOT_FILLED",
            "PENDING_REPLACE,3,PARTIALLY_FILLED_OPEN",
            "PARTIAL_FILLED,0,PARTIALLY_FILLED_OPEN",
            "PARTIAL_FILLED,3,PARTIALLY_FILLED_OPEN",
            "FILLED,10,TERMINAL_WITH_FILL",
            "FILLED,0,TERMINAL_WITHOUT_FILL",
            "CANCELED,0,TERMINAL_WITHOUT_FILL",
            "CANCELED,4,TERMINAL_WITH_FILL",
            "REJECTED,0,TERMINAL_WITHOUT_FILL",
            "REJECTED,4,TERMINAL_WITH_FILL",
            "REPLACED,0,TERMINAL_WITHOUT_FILL",
            "REPLACED,4,TERMINAL_WITH_FILL",
            "CANCEL_REJECTED,0,CONTROL_RECORD",
            "CANCEL_REJECTED,4,CONTROL_RECORD",
            "REPLACE_REJECTED,0,CONTROL_RECORD",
            "REPLACE_REJECTED,4,CONTROL_RECORD",
            "SETTLED_BY_BROKER,4,UNKNOWN"
    })
    void judgesLifecycleFromProviderStatusAndFilledQuantity(
            String providerStatus,
            String filledQuantity,
            BrokerOrderLifecycle expected
    ) {
        givenToken();
        boolean filled = !"0".equals(filledQuantity);
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withSuccess(
                        singleOrder(
                                providerStatus,
                                filledQuantity,
                                filled ? "\"200.00\"" : null,
                                filled ? "\"2026-09-03T00:12:31+09:00\"" : null),
                        MediaType.APPLICATION_JSON));

        BrokerOrderHistoryPage page = provider.fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage());

        BrokerOrderRecord record = page.records().getFirst();
        assertThat(record.lifecycle()).isEqualTo(expected);
        assertThat(record.providerStatusCode()).isEqualTo(providerStatus);
        assertThat(record.isLedgerEligible()).isEqualTo(expected == BrokerOrderLifecycle.TERMINAL_WITH_FILL);
        server.verify();
    }

    /** 체결분이 있어도 최종 체결 시각이 없으면 원장 후보가 아니다. 결제 예정일로 시각을 합성하지 않는다. */
    @Test
    void doesNotTreatFilledOrderWithoutExecutionTimeAsLedgerCandidate() {
        givenToken();
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withSuccess(
                        singleOrder("FILLED", "10", "\"221.86\"", null), MediaType.APPLICATION_JSON));

        BrokerOrderRecord record = provider
                .fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage())
                .records()
                .getFirst();

        assertThat(record.lifecycle()).isEqualTo(BrokerOrderLifecycle.TERMINAL_WITH_FILL);
        assertThat(record.filledAt()).isNull();
        assertThat(record.settlementDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(record.isLedgerEligible()).isFalse();
        server.verify();
    }

    /** 명세는 미지의 enum 값을 허용하도록 요구한다. 파싱을 실패시키지 않고 건수로 보고한다. */
    @Test
    void countsUnknownEnumValuesInsteadOfFailing() {
        givenToken();
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withSuccess("""
                        {
                          "result": {
                            "orders": [
                              %s,
                              %s
                            ],
                            "nextCursor": null,
                            "hasNext": false
                          }
                        }
                        """.formatted(
                        orderJson("SOXL", "USD", "SELL", "TRAILING_STOP", "GTC", "SETTLED_BY_BROKER",
                                "5", "5", "\"20.50\"", "\"2026-09-03T00:12:31+09:00\""),
                        orderJson("AAPL", "USD", "BUY", "LIMIT", "DAY", "FILLED",
                                "10", "10", "\"221.86\"", "\"2026-09-03T00:12:31+09:00\"")
                ), MediaType.APPLICATION_JSON));

        BrokerOrderHistoryPage page = provider.fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage());

        assertThat(page.exclusions().fetchedCount()).isEqualTo(2);
        assertThat(page.exclusions().unknownEnumCount()).isEqualTo(1);
        assertThat(page.exclusions().excludedCount()).isZero();
        assertThat(page.records()).hasSize(2);
        assertThat(page.records().getFirst().lifecycle()).isEqualTo(BrokerOrderLifecycle.UNKNOWN);
        assertThat(page.records().getFirst().providerOrderType()).isEqualTo("TRAILING_STOP");
        assertThat(page.records().getFirst().providerTimeInForce()).isEqualTo("GTC");
        assertThat(page.records().getLast().lifecycle()).isEqualTo(BrokerOrderLifecycle.TERMINAL_WITH_FILL);
        server.verify();
    }

    /**
     * 주문 응답에는 시장 필드가 없다. 통화와 종목 코드 표기로만 추론하므로 애매한 건은 통과시키지 않고
     * 사유별로 나눠 보고한다. 조회 건수와 제외 건수의 합은 항상 반환 건수와 같아야 한다.
     *
     * <p>US(USD, 영문 티커)와 KR(KRW, KRX 6자리 숫자)은 둘 다 지원 대상이다(2026-09-16 확장).
     * 통화와 종목 코드 표기가 서로 다른 시장을 가리키는 조합(예: 6자리 숫자인데 USD)은 여전히
     * 애매한 것으로 보고 제외한다.
     */
    @Test
    void includesUsAndKrOrdersButExcludesAmbiguousOrUnknownCombinations() {
        givenToken();
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withSuccess("""
                        {
                          "result": {
                            "orders": [%s, %s, %s, %s],
                            "nextCursor": null,
                            "hasNext": false
                          }
                        }
                        """.formatted(
                        orderJson("AAPL", "USD", "BUY", "LIMIT", "DAY", "FILLED",
                                "10", "10", "\"221.86\"", "\"2026-09-03T00:12:31+09:00\""),
                        orderJson("005930", "KRW", "BUY", "LIMIT", "DAY", "FILLED",
                                "10", "10", "\"65000\"", "\"2026-09-03T09:12:31+09:00\""),
                        orderJson("7203", "JPY", "BUY", "LIMIT", "DAY", "FILLED",
                                "5", "5", "\"2500\"", "\"2026-09-03T09:12:31+09:00\""),
                        orderJson("123456", "USD", "BUY", "LIMIT", "DAY", "FILLED",
                                "1", "1", "\"10\"", "\"2026-09-03T00:12:31+09:00\"")
                ), MediaType.APPLICATION_JSON));

        BrokerOrderHistoryPage page = provider.fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage());

        // AAPL/USD는 US로, 005930/KRW는 KR로 각각 통화·종목코드 표기가 일치해 반영된다.
        assertThat(page.records()).extracting(BrokerOrderRecord::ticker).containsExactly("AAPL", "005930");
        assertThat(page.records()).extracting(BrokerOrderRecord::market)
                .containsExactly(Market.US, Market.KR);
        assertThat(page.exclusions().fetchedCount()).isEqualTo(4);
        // JPY(7203)만 미지원 통화다.
        assertThat(page.exclusions().unsupportedCurrencyCount()).isEqualTo(1);
        // 123456/USD는 종목코드가 KRX 숫자 표기인데 통화가 USD라 시장이 애매해 제외된다.
        assertThat(page.exclusions().unsupportedMarketCount()).isEqualTo(1);
        // JPY는 미지의 통화이기도 하므로 신호 건수에도 잡힌다. 제외 건수와는 별개 축이다.
        assertThat(page.exclusions().unknownEnumCount()).isEqualTo(1);
        assertThat(page.records().size() + page.exclusions().excludedCount())
                .isEqualTo(page.exclusions().fetchedCount());
        server.verify();
    }

    @Test
    void preservesLongDecimalStringsWithinTheSpecifiedLength() {
        givenToken();
        String longDecimal = "12345678901234567890.123456789";
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withSuccess(
                        singleOrder("FILLED", longDecimal, "\"" + longDecimal + "\"",
                                "\"2026-09-03T00:12:31+09:00\""),
                        MediaType.APPLICATION_JSON));

        BrokerOrderRecord record = provider
                .fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage())
                .records()
                .getFirst();

        assertThat(longDecimal).hasSize(30);
        assertThat(record.filledQuantity()).isEqualTo(new BigDecimal(longDecimal));
        assertThat(record.averageFilledPrice()).isEqualTo(new BigDecimal(longDecimal));
        server.verify();
    }

    @Test
    void failsWhenResultIsNotAnOrderPage() {
        givenToken();
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withSuccess("""
                        {"result": {"nextCursor": null, "hasNext": false}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage()))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("토스증권 주문 이력 응답이 올바르지 않습니다.");

        server.verify();
    }

    @Test
    void failsWhenDecimalStringFieldIsNotUsable() {
        givenToken();
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withSuccess(
                        singleOrder("FILLED", "십", "\"221.86\"", "\"2026-09-03T00:12:31+09:00\""),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage()))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("토스증권 주문 이력 응답이 올바르지 않습니다.");

        server.verify();
    }

    /** 방향은 명세가 미지의 값을 허용하지 않는 필수 필드다. 모르면 거래로 읽지 않는다. */
    @Test
    void failsWhenOrderSideIsNotUsable() {
        givenToken();
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withSuccess("""
                        {"result": {"orders": [%s], "nextCursor": null, "hasNext": false}}
                        """.formatted(orderJson("AAPL", "USD", "SHORT", "LIMIT", "DAY", "FILLED",
                        "10", "10", "\"221.86\"", "\"2026-09-03T00:12:31+09:00\"")),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage()))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("토스증권 주문 이력 응답이 올바르지 않습니다.");

        server.verify();
    }

    /** 증권사 원문 오류 본문과 자격 증명은 예외 메시지에 담지 않는다. */
    @Test
    void failsWithoutLeakingBrokerErrorBodyOrCredentials() {
        givenToken();
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error": {"requestId": "req-1", "code": "rate-limit-exceeded",
                                 "message": "한도를 초과했습니다."}}
                                """));

        assertThatThrownBy(() -> provider.fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage()))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("토스증권 주문 이력 조회에 실패했습니다.")
                .extracting(Throwable::getMessage)
                .asString()
                .doesNotContain("rate-limit-exceeded", "req-1", "secret", "test-access-token");

        server.verify();
    }

    @Test
    void dropsCachedTokenWhenBrokerRejectsIt() {
        givenToken();
        server.expect(requestTo(startsWith(ORDERS_URL)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.fetchOrders(tossCredentials("id", "secret"), "1", closedFirstPage()))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("토스증권 주문 이력 조회에 실패했습니다.");

        verify(accessTokenIssuer).invalidate(tossCredentials("id", "secret"));
        server.verify();
    }

    @Test
    void rejectsMissingAccountSequenceBeforeCallingBroker() {
        assertThatThrownBy(() -> provider.fetchOrders(tossCredentials("id", "secret"), " ", closedFirstPage()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("증권사 계좌 일련번호가 필요합니다.");

        verifyNoInteractions(accessTokenIssuer);
        server.verify();
    }

    @Test
    void rejectsMissingQueryBeforeCallingBroker() {
        assertThatThrownBy(() -> provider.fetchOrders(tossCredentials("id", "secret"), "1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 이력 조회 조건이 필요합니다.");

        verifyNoInteractions(accessTokenIssuer);
        server.verify();
    }

    private void givenToken() {
        when(accessTokenIssuer.issueAccessToken(tossCredentials("id", "secret"))).thenReturn("test-access-token");
    }

    private BrokerOrderHistoryQuery closedFirstPage() {
        return BrokerOrderHistoryQuery.closedFirstPage(FROM, TO, BrokerOrderHistoryQuery.MAX_PAGE_SIZE);
    }

    private String singleOrder(
            String status,
            String filledQuantity,
            String averageFilledPriceJson,
            String filledAtJson
    ) {
        return """
                {
                  "result": {
                    "orders": [%s],
                    "nextCursor": null,
                    "hasNext": false
                  }
                }
                """.formatted(orderJson("AAPL", "USD", "BUY", "LIMIT", "DAY", status,
                "10", filledQuantity, averageFilledPriceJson, filledAtJson));
    }

    private String orderJson(
            String symbol,
            String currency,
            String side,
            String orderType,
            String timeInForce,
            String status,
            String quantity,
            String filledQuantity,
            String averageFilledPriceJson,
            String filledAtJson
    ) {
        return """
                {
                  "orderId": "order-%s-%s",
                  "symbol": "%s",
                  "side": "%s",
                  "orderType": "%s",
                  "timeInForce": "%s",
                  "status": "%s",
                  "price": "220.00",
                  "quantity": "%s",
                  "orderAmount": null,
                  "currency": "%s",
                  "orderedAt": "2026-09-02T23:35:00+09:00",
                  "canceledAt": null,
                  "execution": {
                    "filledQuantity": "%s",
                    "averageFilledPrice": %s,
                    "filledAmount": null,
                    "commission": null,
                    "tax": null,
                    "filledAt": %s,
                    "settlementDate": "2026-09-05"
                  }
                }
                """.formatted(symbol, status, symbol, side, orderType, timeInForce, status,
                quantity, currency, filledQuantity,
                averageFilledPriceJson == null ? "null" : averageFilledPriceJson,
                filledAtJson == null ? "null" : filledAtJson);
    }
}
