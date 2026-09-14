package com.tradeguide.service.backtest;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MomentumRebalanceSchedulerTest {

    private final MomentumRebalanceScheduler scheduler = new MomentumRebalanceScheduler();

    @Test
    void schedulesFirstRebalanceAtFiftyThirdWeekThenEveryInterval() {
        // Given: 79주 캘린더(index 0~78), 13주 리밸런싱 주기
        List<LocalDate> calendar = weeklyCalendar(79);

        // When
        List<LocalDate> rebalanceDates = scheduler.schedule(calendar, 13);

        // Then: index 52, 65, 78 (91은 캘린더 범위 밖이라 제외)
        assertThat(rebalanceDates).containsExactly(
                calendar.get(52),
                calendar.get(65),
                calendar.get(78)
        );
    }

    @Test
    void returnsEmptyWhenCalendarIsShorterThanFormationPeriod() {
        List<LocalDate> calendar = weeklyCalendar(52);

        List<LocalDate> rebalanceDates = scheduler.schedule(calendar, 13);

        assertThat(rebalanceDates).isEmpty();
    }

    @Test
    void throwsWhenRebalanceIntervalIsLessThanOneWeek() {
        List<LocalDate> calendar = weeklyCalendar(60);

        assertThatIllegalArgumentException().isThrownBy(() -> scheduler.schedule(calendar, 0));
    }

    @Test
    void throwsWhenCalendarIsNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> scheduler.schedule(null, 13));
    }

    private List<LocalDate> weeklyCalendar(int count) {
        List<LocalDate> calendar = new ArrayList<>();
        LocalDate start = LocalDate.of(2020, 1, 3);
        for (int i = 0; i < count; i++) {
            calendar.add(start.plusWeeks(i));
        }
        return calendar;
    }
}
