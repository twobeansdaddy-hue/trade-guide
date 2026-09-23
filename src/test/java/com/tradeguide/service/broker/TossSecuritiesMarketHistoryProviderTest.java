package com.tradeguide.service.broker;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.tradeguide.domain.broker.BrokerCredentialsFixture.tossCredentials;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossSecuritiesMarketHistoryProviderTest {

    private static final String BASE_URL = "https://openapi.example.test";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TossSecuritiesAccessTokenIssuer tokenIssuer = mock(TossSecuritiesAccessTokenIssuer.class);
    private final TossSecuritiesMarketHistoryProvider provider =
            new TossSecuritiesMarketHistoryProvider(builder, BASE_URL, tokenIssuer,
                    Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void overlappingDailyPagesYieldUniqueDates() {
        givenToken();
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page("page2", candle("2026-09-04", "104"),
                        candle("2026-09-03", "103")), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page(null, candle("2026-09-03", "103.0"),
                        candle("2026-09-02", "102")), MediaType.APPLICATION_JSON));

        List<MarketCandle> candles = provider.getCandles(tossCredentials("id", "secret"),
                Market.US, "SOXL", CandleInterval.DAILY, 3);

        assertThat(candles).extracting(MarketCandle::getTradingDate).containsExactly(
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4));
        server.verify();
    }

    @Test
    void capturesActualResponseTimesForEachTossPage() {
        givenToken();
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page("page2", candle("2026-09-04", "104")),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page(null, candle("2026-09-03", "103")),
                        MediaType.APPLICATION_JSON));

        var result = provider.getCandlesWithReceipt(tossCredentials("id", "secret"),
                Market.US, "SOXL", CandleInterval.DAILY, 2);

        assertThat(result.candles()).hasSize(2);
        assertThat(result.pageReceivedAt()).containsExactly(
                Instant.parse("2026-09-14T12:00:00Z"), Instant.parse("2026-09-14T12:00:00Z"));
        assertThat(result.adjustedRequested()).isTrue();
        server.verify();
    }

    @Test
    void conflictingDuplicateDateFailsInsteadOfDistortingWeeklyVolume() {
        givenToken();
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page("page2", candle("2026-09-04", "104"),
                        candle("2026-09-03", "103")), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page(null, candle("2026-09-03", "999")),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getCandles(tossCredentials("id", "secret"),
                Market.US, "SOXL", CandleInterval.WEEKLY, 1))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("동일 날짜");
        server.verify();
    }

    @Test
    void overlappingPagesDoNotDoubleCountWeeklyVolume() {
        givenToken();
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page("page2", candle("2026-09-09", "109"),
                        candle("2026-09-08", "108")), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page(null, candle("2026-09-08", "108"),
                        candle("2026-09-07", "107")), MediaType.APPLICATION_JSON));

        List<MarketCandle> candles = provider.getCandles(tossCredentials("id", "secret"),
                Market.US, "SOXL", CandleInterval.WEEKLY, 1);

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().getTradingDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(candles.getFirst().getVolume()).isEqualTo(300L);
        server.verify();
    }

    @Test
    void repeatedPageCursorFailsWithoutUnboundedRequests() {
        givenToken();
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page("page2", candle("2026-09-04", "104")),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page("page2", candle("2026-09-03", "103")),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getCandles(tossCredentials("id", "secret"),
                Market.US, "SOXL", CandleInterval.DAILY, 3))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("커서");
        server.verify();
    }

    @Test
    void duplicateOnlyPageFailsWithoutUnboundedRequests() {
        givenToken();
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page("page2", candle("2026-09-04", "104")),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andRespond(withSuccess(page("page3", candle("2026-09-04", "104")),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getCandles(tossCredentials("id", "secret"),
                Market.US, "SOXL", CandleInterval.DAILY, 3))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("새 날짜");
        server.verify();
    }

    /** 2026-09-23 실제 관측(IBIT, CRCL): 이력 끝은 요청보다 적은 캔들과 nextBefore=null을 함께 준다. */
    @Test
    void historyEndReturnsShortPageWithNullCursorAndStopsWithoutExtraRequest() {
        givenToken();
        List<String> weekdays = weekdaysBackwardFrom(LocalDate.of(2026, 9, 22), 240);
        server.expect(requestTo(allOf(startsWith(BASE_URL + "/api/v1/candles?"), not(containsString("before=")))))
                .andExpect(queryParam("count", "200"))
                .andRespond(withSuccess(page("page2", candles(weekdays.subList(0, 200))),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/api/v1/candles?")))
                .andExpect(queryParam("count", "100"))
                .andExpect(queryParam("before", "page2"))
                .andRespond(withSuccess(page(null, candles(weekdays.subList(200, 240))),
                        MediaType.APPLICATION_JSON));

        var result = provider.getCandlesWithReceipt(tossCredentials("id", "secret"),
                Market.US, "IBIT", CandleInterval.DAILY, 300);

        assertThat(result.candles()).hasSize(240);
        assertThat(result.candles()).extracting(MarketCandle::getTradingDate)
                .isSortedAccordingTo(Comparator.naturalOrder())
                .doesNotHaveDuplicates()
                .startsWith(LocalDate.parse(weekdays.get(239)))
                .endsWith(LocalDate.of(2026, 9, 22));
        assertThat(result.pageReceivedAt()).hasSize(2);
        server.verify();
    }

    private void givenToken() {
        when(tokenIssuer.issueAccessToken(any())).thenReturn("test-access-token");
    }

    private String page(String nextBefore, String... candles) {
        return "{\"result\":{\"candles\":[" + String.join(",", candles)
                + "],\"nextBefore\":" + (nextBefore == null ? "null" : "\"" + nextBefore + "\"") + "}}";
    }

    private String candle(String date, String close) {
        return "{\"timestamp\":\"" + date + "\",\"openPrice\":\"100\","
                + "\"highPrice\":\"110\",\"lowPrice\":\"90\",\"closePrice\":\""
                + close + "\",\"volume\":\"100\",\"currency\":\"USD\"}";
    }

    private String[] candles(List<String> dates) {
        return dates.stream().map(date -> candle(date, "100")).toArray(String[]::new);
    }

    private List<String> weekdaysBackwardFrom(LocalDate latest, int count) {
        List<String> dates = new ArrayList<>();
        for (LocalDate date = latest; dates.size() < count; date = date.minusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(date.toString());
            }
        }
        return dates;
    }
}
