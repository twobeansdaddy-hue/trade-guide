package com.tradeguide.service.market;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwelveDataMarketHistoryProviderTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder();

    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(restClientBuilder).build();

    private final TwelveDataMarketHistoryProvider provider =
            new TwelveDataMarketHistoryProvider(
                    restClientBuilder,
                    "test-api-key"
            );

    @Test
    void returnsDailyCandlesInAscendingOrder() {
        // Given
        server.expect(
                        requestTo(
                                "https://api.twelvedata.com/time_series"
                                        + "?symbol=AAPL&interval=1day"
                                        + "&outputsize=2&order=asc"
                        )
                )
                .andExpect(
                        header(
                                HttpHeaders.AUTHORIZATION,
                                "apikey test-api-key"
                        )
                )
                .andRespond(
                        withSuccess("""
                                {
                                  "values": [
                                    {
                                      "datetime": "2026-08-03",
                                      "open": "200.10",
                                      "high": "203.00",
                                      "low": "199.50",
                                      "close": "202.50",
                                      "volume": "1000000"
                                    },
                                    {
                                      "datetime": "2026-08-04",
                                      "open": "202.80",
                                      "high": "205.00",
                                      "low": "201.30",
                                      "close": "204.10",
                                      "volume": "1200000"
                                    }
                                  ]
                                }
                                """, MediaType.APPLICATION_JSON)
                );

        // When
        List<MarketCandle> candles =
                provider.getCandles(Market.US, "aapl", CandleInterval.DAILY, 2);

        // Then
        assertThat(candles).hasSize(2);

        MarketCandle firstCandle = candles.get(0);
        assertThat(firstCandle.getMarket()).isEqualTo(Market.US);
        assertThat(firstCandle.getTicker()).isEqualTo("AAPL");
        assertThat(firstCandle.getTradingDate())
                .isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(firstCandle.getOpen())
                .isEqualByComparingTo("200.10");
        assertThat(firstCandle.getHigh())
                .isEqualByComparingTo("203.00");
        assertThat(firstCandle.getLow())
                .isEqualByComparingTo("199.50");
        assertThat(firstCandle.getClose())
                .isEqualByComparingTo("202.50");
        assertThat(firstCandle.getVolume()).isEqualTo(1_000_000L);

        assertThat(candles.get(1).getTradingDate())
                .isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(candles.get(1).getClose())
                .isEqualByComparingTo("204.10");

        server.verify();
    }

    @Test
    void returnsWeeklyCandlesInAscendingOrder() {
        // Given
        server.expect(
                        requestTo(
                                "https://api.twelvedata.com/time_series"
                                        + "?symbol=SOXL&interval=1week"
                                        + "&outputsize=2&order=asc"
                        )
                )
                .andRespond(
                        withSuccess("""
                            {
                              "values": [
                                {
                                  "datetime": "2026-07-24",
                                  "open": "20.10",
                                  "high": "22.00",
                                  "low": "19.50",
                                  "close": "21.50",
                                  "volume": "1000000"
                                },
                                {
                                  "datetime": "2026-07-31",
                                  "open": "21.80",
                                  "high": "23.00",
                                  "low": "20.30",
                                  "close": "22.10",
                                  "volume": "1200000"
                                }
                              ]
                            }
                            """, MediaType.APPLICATION_JSON)
                );

        // When
        List<MarketCandle> candles = provider.getCandles(
                Market.US,
                "soxl",
                CandleInterval.WEEKLY,
                2
        );

        // Then
        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).getTicker()).isEqualTo("SOXL");
        assertThat(candles.get(0).getTradingDate())
                .isEqualTo(LocalDate.of(2026, 7, 24));
        assertThat(candles.get(1).getTradingDate())
                .isEqualTo(LocalDate.of(2026, 7, 31));

        server.verify();
    }

    @Test
    void throwsExceptionWhenTwelveDataRateLimitIsExceeded() {
        // Given
        server.expect(
                        requestTo(
                                "https://api.twelvedata.com/time_series"
                                        + "?symbol=AAPL&interval=1day"
                                        + "&outputsize=2&order=asc"
                        )
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

        // When & Then
        assertThatThrownBy(() ->
                provider.getCandles(Market.US, "AAPL", CandleInterval.DAILY, 2)
        )
                .isInstanceOf(MarketDataRateLimitExceededException.class)
                .hasMessage(
                        "시장 데이터 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요."
                );

        server.verify();
    }

    @Test
    void throwsExceptionWhenOutputSizeIsOutsideAllowedRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        provider.getCandles(Market.US, "AAPL", CandleInterval.DAILY,0)
                )
                .withMessage("캔들 조회 개수는 1에서 5000 사이여야 합니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        provider.getCandles(Market.US, "AAPL", CandleInterval.DAILY,5001)
                )
                .withMessage("캔들 조회 개수는 1에서 5000 사이여야 합니다.");
    }

    @Test
    void throwsExceptionWhenApiKeyIsBlank() {
        // Given
        TwelveDataMarketHistoryProvider providerWithoutApiKey =
                new TwelveDataMarketHistoryProvider(
                        RestClient.builder(),
                        ""
                );

        // When & Then
        assertThatThrownBy(() ->
                providerWithoutApiKey.getCandles(
                        Market.US,
                        "AAPL",
                        CandleInterval.DAILY,
                        2
                )
        )
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessage("시장 데이터 조회 API 키가 설정되지 않았습니다.");
    }
}