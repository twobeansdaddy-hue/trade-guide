package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompletedWeeklyCandleFilterTest {

    @Test
    void excludesCurrentWeekCandleBeforeFridayMarketClose() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-07T19:59:00Z"),
                ZoneOffset.UTC
        );
        CompletedWeeklyCandleFilter filter =
                new CompletedWeeklyCandleFilter(new WeeklyCandleSchedule(clock));

        List<MarketCandle> result = filter.filter(List.of(
                candle(LocalDate.of(2026, 7, 27)),
                candle(LocalDate.of(2026, 8, 3))
        ));

        assertThat(result)
                .extracting(MarketCandle::getTradingDate)
                .containsExactly(LocalDate.of(2026, 7, 27));
    }

    @Test
    void includesCurrentWeekCandleAfterFridayMarketClose() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-07T20:15:00Z"),
                ZoneOffset.UTC
        );
        CompletedWeeklyCandleFilter filter =
                new CompletedWeeklyCandleFilter(new WeeklyCandleSchedule(clock));

        List<MarketCandle> result = filter.filter(List.of(
                candle(LocalDate.of(2026, 7, 27)),
                candle(LocalDate.of(2026, 8, 3))
        ));

        assertThat(result)
                .extracting(MarketCandle::getTradingDate)
                .containsExactly(
                        LocalDate.of(2026, 7, 27),
                        LocalDate.of(2026, 8, 3)
                );
    }

    @Test
    void excludesCurrentWeekCandleWhenWeekStartsOnTuesdayAfterHoliday() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-01-21T15:00:00Z"),
                ZoneOffset.UTC
        );
        CompletedWeeklyCandleFilter filter =
                new CompletedWeeklyCandleFilter(new WeeklyCandleSchedule(clock));

        List<MarketCandle> result = filter.filter(List.of(
                candle(LocalDate.of(2026, 1, 12)),
                candle(LocalDate.of(2026, 1, 20))
        ));

        assertThat(result)
                .extracting(MarketCandle::getTradingDate)
                .containsExactly(LocalDate.of(2026, 1, 12));
    }

    private MarketCandle candle(LocalDate tradingDate) {
        return new MarketCandle(
                Market.US,
                "SOXL",
                tradingDate,
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("90"),
                new BigDecimal("105"),
                1_000L
        );
    }
}
