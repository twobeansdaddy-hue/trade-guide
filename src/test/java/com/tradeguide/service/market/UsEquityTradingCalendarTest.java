package com.tradeguide.service.market;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UsEquityTradingCalendarTest {

    private final UsEquityTradingCalendar calendar = new UsEquityTradingCalendar();

    @Test
    void recognizesMajorUsMarketHolidaysAndObservedDates() {
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 1, 1))).isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 7, 3))).isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 7, 6))).isTrue();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 11, 26))).isFalse();
    }

    @Test
    void resolvesNextTradingDayForWeekendAndHoliday() {
        assertThat(calendar.nextTradingDayOrSame(LocalDate.of(2026, 7, 4)))
                .isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(calendar.nextTradingDayOrSame(LocalDate.of(2026, 7, 6)))
                .isEqualTo(LocalDate.of(2026, 7, 6));
    }
}
