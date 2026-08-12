package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.StaleMarketDataException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeeklyCandleFreshnessValidatorTest {

    @Test
    void acceptsCandlesWhenLatestCompletedCandleIsFresh() {
        WeeklyCandleFreshnessValidator validator = validatorAt(
                "2026-08-12T15:00:00Z"
        );

        assertThatCode(() -> validator.validate(List.of(
                candle(LocalDate.of(2026, 8, 3))
        ))).doesNotThrowAnyException();
    }

    @Test
    void throwsExceptionWhenLatestCompletedCandleIsStale() {
        WeeklyCandleFreshnessValidator validator = validatorAt(
                "2026-08-12T15:00:00Z"
        );

        assertThatThrownBy(() -> validator.validate(List.of(
                candle(LocalDate.of(2026, 7, 27))
        )))
                .isInstanceOf(StaleMarketDataException.class)
                .hasMessage("최신 완료 주봉 데이터가 오래되었습니다.");
    }

    @Test
    void throwsExceptionWhenThereAreNoCompletedCandles() {
        WeeklyCandleFreshnessValidator validator = validatorAt(
                "2026-08-12T15:00:00Z"
        );

        assertThatThrownBy(() -> validator.validate(List.of()))
                .isInstanceOf(StaleMarketDataException.class)
                .hasMessage("완료된 주봉 데이터가 없습니다.");
    }

    private WeeklyCandleFreshnessValidator validatorAt(
            String instant
    ) {
        Clock clock = Clock.fixed(
                Instant.parse(instant),
                ZoneOffset.UTC
        );
        WeeklyCandleSchedule schedule =
                new WeeklyCandleSchedule(clock);

        return new WeeklyCandleFreshnessValidator(schedule);
    }

    private MarketCandle candle(LocalDate tradingDate) {
        BigDecimal price = new BigDecimal("100");

        return new MarketCandle(
                Market.US,
                "SOXL",
                tradingDate,
                price,
                price,
                price,
                price,
                1_000L
        );
    }
}
