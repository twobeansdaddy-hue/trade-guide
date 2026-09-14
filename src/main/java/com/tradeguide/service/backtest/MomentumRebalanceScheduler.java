package com.tradeguide.service.backtest;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 유니버스 전체의 거래일 캘린더(오름차순, 중복 없음)로부터 리밸런싱 시점을
 * 계산하는 순수 계산기다. 리밸런싱 주기는 호출자가 명시적으로 지정한다
 * ({@code rebalanceIntervalWeeks}) — 분기(13주)를 기본값으로 숨기지 않는다.
 *
 * 첫 리밸런싱 시점은 52주 형성기간을 계산할 수 있는 최초 시점(캘린더
 * 인덱스 52, 즉 53번째 거래일)이다. 그 이후로는 지정한 주기마다 리밸런싱
 * 시점을 추가한다.
 */
@Component
public class MomentumRebalanceScheduler {

    public List<LocalDate> schedule(
            List<LocalDate> ascendingCalendar,
            int rebalanceIntervalWeeks
    ) {
        if (ascendingCalendar == null) {
            throw new IllegalArgumentException("리밸런싱 시점 계산에는 거래일 캘린더가 필요합니다.");
        }

        if (rebalanceIntervalWeeks < 1) {
            throw new IllegalArgumentException("리밸런싱 주기는 1주 이상이어야 합니다.");
        }

        int firstEligibleIndex = MomentumFormationReturnCalculator.LOOKBACK_WEEKS;
        List<LocalDate> rebalanceDates = new ArrayList<>();

        for (int index = firstEligibleIndex; index < ascendingCalendar.size(); index += rebalanceIntervalWeeks) {
            rebalanceDates.add(ascendingCalendar.get(index));
        }

        return rebalanceDates;
    }
}
