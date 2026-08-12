package com.tradeguide.service.market;

import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

@Component
public class WeeklyCandleSchedule {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime REGULAR_MARKET_CLOSE = LocalTime.of(16, 15);

    private final Clock clock;

    public WeeklyCandleSchedule(Clock clock) {
        this.clock = clock;
    }

    public LocalDate getFirstIncompleteCandleStart() {
        ZonedDateTime now = ZonedDateTime.now(clock)
                .withZoneSameInstant(NEW_YORK_ZONE);

        LocalDate today = now.toLocalDate();

        if (now.getDayOfWeek() == DayOfWeek.SATURDAY
                || now.getDayOfWeek() == DayOfWeek.SUNDAY
                || (now.getDayOfWeek() == DayOfWeek.FRIDAY
                && !now.toLocalTime().isBefore(REGULAR_MARKET_CLOSE))) {
            return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }

        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate getExpectedLatestCompletedCandleStart() {
        return getFirstIncompleteCandleStart().minusWeeks(1);
    }

}
