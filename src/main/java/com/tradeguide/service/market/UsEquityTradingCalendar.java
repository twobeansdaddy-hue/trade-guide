package com.tradeguide.service.market;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

/**
 * 미국 주식 정규장의 거래일을 계산한다.
 *
 * <p>초기 운영 범위에 필요한 NYSE 주요 휴장일을 계산식으로 관리한다. 조기 폐장 시각은
 * 이 클래스의 책임이 아니며, 장전 가이드 생성 여부만 판정한다.
 */
@Component
public class UsEquityTradingCalendar {

    public boolean isTradingDay(LocalDate date) {
        if (date == null || date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }

        return !holidaysForYear(date.getYear()).contains(date);
    }

    public LocalDate nextTradingDayOrSame(LocalDate date) {
        LocalDate candidate = date;
        while (!isTradingDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private Set<LocalDate> holidaysForYear(int year) {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(observedFixedHoliday(year, Month.JANUARY, 1));
        holidays.add(nthWeekday(year, Month.JANUARY, DayOfWeek.MONDAY, 3));
        holidays.add(nthWeekday(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3));
        holidays.add(goodFriday(year));
        holidays.add(lastWeekday(year, Month.MAY, DayOfWeek.MONDAY));
        if (year >= 2022) {
            holidays.add(observedFixedHoliday(year, Month.JUNE, 19));
        }
        holidays.add(observedFixedHoliday(year, Month.JULY, 4));
        holidays.add(nthWeekday(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1));
        holidays.add(nthWeekday(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4));
        holidays.add(observedFixedHoliday(year, Month.DECEMBER, 25));

        // Jan 1이 토요일이면 관측 휴일은 전년도 12월 31일이다.
        LocalDate nextNewYearObserved = observedFixedHoliday(year + 1, Month.JANUARY, 1);
        if (nextNewYearObserved.getYear() == year) {
            holidays.add(nextNewYearObserved);
        }

        return holidays;
    }

    private LocalDate observedFixedHoliday(int year, Month month, int dayOfMonth) {
        LocalDate holiday = LocalDate.of(year, month, dayOfMonth);
        if (holiday.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return holiday.minusDays(1);
        }
        if (holiday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return holiday.plusDays(1);
        }
        return holiday;
    }

    private LocalDate nthWeekday(
            int year,
            Month month,
            DayOfWeek dayOfWeek,
            int occurrence
    ) {
        return LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(occurrence, dayOfWeek));
    }

    private LocalDate lastWeekday(int year, Month month, DayOfWeek dayOfWeek) {
        return LocalDate.of(year, month, month.length(java.time.Year.isLeap(year)))
                .with(TemporalAdjusters.lastInMonth(dayOfWeek));
    }

    private LocalDate goodFriday(int year) {
        return easterSunday(year).minusDays(2);
    }

    // Gregorian computus. 미국 주식 휴장일 계산에 필요한 부활절 날짜만 산출한다.
    private LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
