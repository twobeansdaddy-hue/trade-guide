package com.tradeguide.domain.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 횡단면 모멘텀 검증 백테스트의 전체 실행 결과.
 *
 * 이 결과는 전략 채택을 의미하지 않는다. {@code pointInTimeUniverse}가
 * {@code false}이면 입력 유니버스가 현재 시점 구성을 그대로 과거에 사용한
 * 것이므로 생존 편향이 있을 수 있다는 뜻이고, 코드가 이를 보정했다는 의미가
 * 아니다.
 */
public class CrossSectionalMomentumBacktestResult {

    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final BigDecimal startingPortfolioValue;
    private final BigDecimal endingPortfolioValue;
    private final BigDecimal cumulativeReturnRate;
    private final List<CrossSectionalMomentumRebalanceEvent> rebalanceEvents;
    private final List<MomentumDataQualityExclusion> universeExclusions;
    private final BacktestAssumptions assumptions;
    private final boolean pointInTimeUniverse;
    private final int rebalanceIntervalWeeks;
    private final BigDecimal topTierFraction;
    private final BigDecimal holdTierFraction;

    public CrossSectionalMomentumBacktestResult(
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal startingPortfolioValue,
            BigDecimal endingPortfolioValue,
            BigDecimal cumulativeReturnRate,
            List<CrossSectionalMomentumRebalanceEvent> rebalanceEvents,
            List<MomentumDataQualityExclusion> universeExclusions,
            BacktestAssumptions assumptions,
            boolean pointInTimeUniverse,
            int rebalanceIntervalWeeks,
            BigDecimal topTierFraction,
            BigDecimal holdTierFraction
    ) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.startingPortfolioValue = startingPortfolioValue;
        this.endingPortfolioValue = endingPortfolioValue;
        this.cumulativeReturnRate = cumulativeReturnRate;
        this.rebalanceEvents = rebalanceEvents;
        this.universeExclusions = universeExclusions;
        this.assumptions = assumptions;
        this.pointInTimeUniverse = pointInTimeUniverse;
        this.rebalanceIntervalWeeks = rebalanceIntervalWeeks;
        this.topTierFraction = topTierFraction;
        this.holdTierFraction = holdTierFraction;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getStartingPortfolioValue() {
        return startingPortfolioValue;
    }

    public BigDecimal getEndingPortfolioValue() {
        return endingPortfolioValue;
    }

    public BigDecimal getCumulativeReturnRate() {
        return cumulativeReturnRate;
    }

    public List<CrossSectionalMomentumRebalanceEvent> getRebalanceEvents() {
        return rebalanceEvents;
    }

    public List<MomentumDataQualityExclusion> getUniverseExclusions() {
        return universeExclusions;
    }

    public BacktestAssumptions getAssumptions() {
        return assumptions;
    }

    public boolean isPointInTimeUniverse() {
        return pointInTimeUniverse;
    }

    public int getRebalanceIntervalWeeks() {
        return rebalanceIntervalWeeks;
    }

    public BigDecimal getTopTierFraction() {
        return topTierFraction;
    }

    public BigDecimal getHoldTierFraction() {
        return holdTierFraction;
    }
}
