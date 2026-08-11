package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Component
public class CompletedWeeklyCandleFilter {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime REGULAR_MARKET_CLOSE = LocalTime.of(16, 15);

    private final Clock clock;

    public CompletedWeeklyCandleFilter(Clock clock) {
        this.clock = clock;
    }

    public List<MarketCandle> filter(List<MarketCandle> candles) {
        ZonedDateTime now = ZonedDateTime.now(clock)
                .withZoneSameInstant(NEW_YORK_ZONE);

        LocalDate completedCandleBefore = getCompletedCandleBefore(now);

        return candles.stream()
                .filter(candle -> candle.getTradingDate().isBefore(completedCandleBefore))
                .toList();

    }

    private LocalDate getCompletedCandleBefore(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();

        if (now.getDayOfWeek() == DayOfWeek.SATURDAY
                || now.getDayOfWeek() == DayOfWeek.SUNDAY
                || (now.getDayOfWeek() == DayOfWeek.FRIDAY
                && !now.toLocalTime().isBefore(REGULAR_MARKET_CLOSE))) {
            return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }

        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
