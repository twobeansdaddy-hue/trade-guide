package com.tradeguide.service.backtest;

import com.tradeguide.domain.market.MarketCandle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 횡단면 모멘텀 후보의 형성기간 수익률을 계산하는 순수 계산기다.
 * 직전 52주 수익률에서 최근 4주를 제외한 48주 수익률
 * (형성기간 종가 / 52주 전 종가 - 1, 백분율)을 반환한다.
 *
 * 입력 {@code candlesUpToAndIncludingRebalanceDate}의 마지막 원소가
 * 리밸런싱 기준일(as-of) 캔들이어야 한다. 이 목록 뒤에 있는 캔들은
 * 이 계산기에 전달하지 않는 방식으로 미래 데이터 참조를 원천 차단한다.
 * 기준일까지의 이력이 52주에 못 미치면 판단을 내리지 않고
 * {@link Optional#empty()}를 반환한다 — 이는 오류가 아니라
 * "이 시점에는 아직 이 종목을 평가할 수 없다"는 명시적 상태다.
 */
@Component
public class MomentumFormationReturnCalculator {

    static final int LOOKBACK_WEEKS = 52;
    static final int EXCLUDE_RECENT_WEEKS = 4;
    private static final int CALCULATION_SCALE = 10;

    public Optional<BigDecimal> calculate(List<MarketCandle> candlesUpToAndIncludingRebalanceDate) {
        if (candlesUpToAndIncludingRebalanceDate == null) {
            throw new IllegalArgumentException("형성기간 수익률 계산에는 캔들 이력이 필요합니다.");
        }

        int size = candlesUpToAndIncludingRebalanceDate.size();
        int baseIndex = size - 1 - LOOKBACK_WEEKS;

        if (baseIndex < 0) {
            return Optional.empty();
        }

        int recentIndex = size - 1 - EXCLUDE_RECENT_WEEKS;

        BigDecimal basePrice = candlesUpToAndIncludingRebalanceDate.get(baseIndex).getClose();
        BigDecimal recentPrice = candlesUpToAndIncludingRebalanceDate.get(recentIndex).getClose();

        if (basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("52주 전 종가는 0보다 커야 형성기간 수익률을 계산할 수 있습니다.");
        }

        BigDecimal formationReturnRate = recentPrice.subtract(basePrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(basePrice, CALCULATION_SCALE, RoundingMode.HALF_UP);

        return Optional.of(formationReturnRate);
    }
}
