package com.tradeguide.service.backtest;

import com.tradeguide.domain.backtest.MomentumFormationScore;
import com.tradeguide.domain.backtest.MomentumTierSelectionResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 순위가 매겨진 형성기간 수익률로부터 상위/유지(top/hold tier) 이원 기준을
 * 적용해 이번 리밸런싱의 보유 종목 구성을 결정하는 순수 계산기다.
 *
 * 신규 진입은 상위 {@code topTierFraction} 안에 들어야 하지만, 이미 보유 중인
 * 종목은 순위가 {@code holdTierFraction}(하한선) 밖으로 완전히 밀려나기
 * 전까지는 유지한다. 이 이원 기준은 임계값 근처를 오가는 종목의 잦은
 * 교체(회전율)를 줄이기 위한 것이며, 두 비율 값 자체가 검증·채택된 상수는
 * 아니므로 호출자가 명시적으로 주입해야 한다.
 */
@Component
public class MomentumTopCandidateSelector {

    public MomentumTierSelectionResult select(
            List<MomentumFormationScore> rankedScores,
            Set<String> previousHoldings,
            BigDecimal topTierFraction,
            BigDecimal holdTierFraction
    ) {
        if (rankedScores == null || previousHoldings == null) {
            throw new IllegalArgumentException("상위 후보 선택에는 순위 목록과 기존 보유 종목 집합이 필요합니다.");
        }

        validateFraction(topTierFraction, "상위 후보 비율");
        validateFraction(holdTierFraction, "유지 하한 비율");

        if (topTierFraction.compareTo(holdTierFraction) > 0) {
            throw new IllegalArgumentException("상위 후보 비율은 유지 하한 비율보다 클 수 없습니다.");
        }

        int universeSize = rankedScores.size();
        int topTierCount = tierCount(universeSize, topTierFraction);
        int holdTierCount = Math.max(topTierCount, tierCount(universeSize, holdTierFraction));

        Set<String> topTierTickers = new LinkedHashSet<>();
        Set<String> holdTierTickers = new LinkedHashSet<>();
        for (MomentumFormationScore score : rankedScores) {
            if (score.getRank() <= topTierCount) {
                topTierTickers.add(score.getTicker());
            }
            if (score.getRank() <= holdTierCount) {
                holdTierTickers.add(score.getTicker());
            }
        }

        Set<String> newHoldings = new LinkedHashSet<>(topTierTickers);
        for (String ticker : previousHoldings) {
            if (holdTierTickers.contains(ticker)) {
                newHoldings.add(ticker);
            }
        }

        List<String> entered = new ArrayList<>();
        List<String> retained = new ArrayList<>();
        for (String ticker : newHoldings) {
            if (previousHoldings.contains(ticker)) {
                retained.add(ticker);
            } else {
                entered.add(ticker);
            }
        }

        List<String> exited = new ArrayList<>();
        for (String ticker : previousHoldings) {
            if (!newHoldings.contains(ticker)) {
                exited.add(ticker);
            }
        }

        return new MomentumTierSelectionResult(newHoldings, entered, retained, exited);
    }

    private int tierCount(int universeSize, BigDecimal fraction) {
        if (universeSize <= 0) {
            return 0;
        }

        int count = BigDecimal.valueOf(universeSize)
                .multiply(fraction)
                .setScale(0, java.math.RoundingMode.FLOOR)
                .intValue();

        return Math.max(1, count);
    }

    private void validateFraction(BigDecimal fraction, String label) {
        if (fraction == null
                || fraction.compareTo(BigDecimal.ZERO) <= 0
                || fraction.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(label + "은 0보다 크고 1 이하여야 합니다.");
        }
    }
}
