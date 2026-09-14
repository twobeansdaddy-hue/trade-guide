package com.tradeguide.domain.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 횡단면 모멘텀 검증에서 한 번의 분기(또는 호출자가 지정한 주기) 리밸런싱
 * 시점의 전체 계산 결과 스냅샷. 순위, 데이터 품질 제외, 편입/유지/이탈 종목,
 * 리밸런싱 전후 포트폴리오 가치와 이번 리밸런싱에서 발생한 거래비용을 담는다.
 */
public class CrossSectionalMomentumRebalanceEvent {

    private final LocalDate rebalanceDate;
    private final List<MomentumFormationScore> rankedScores;
    private final List<MomentumDataQualityExclusion> excludedTickers;
    private final List<String> enteredTickers;
    private final List<String> retainedTickers;
    private final List<String> exitedTickers;
    private final BigDecimal portfolioValueBeforeRebalance;
    private final BigDecimal portfolioValueAfterRebalance;
    private final BigDecimal totalTransactionCost;
    private final Map<String, BigDecimal> holdingWeightsAfter;

    public CrossSectionalMomentumRebalanceEvent(
            LocalDate rebalanceDate,
            List<MomentumFormationScore> rankedScores,
            List<MomentumDataQualityExclusion> excludedTickers,
            List<String> enteredTickers,
            List<String> retainedTickers,
            List<String> exitedTickers,
            BigDecimal portfolioValueBeforeRebalance,
            BigDecimal portfolioValueAfterRebalance,
            BigDecimal totalTransactionCost,
            Map<String, BigDecimal> holdingWeightsAfter
    ) {
        this.rebalanceDate = rebalanceDate;
        this.rankedScores = rankedScores;
        this.excludedTickers = excludedTickers;
        this.enteredTickers = enteredTickers;
        this.retainedTickers = retainedTickers;
        this.exitedTickers = exitedTickers;
        this.portfolioValueBeforeRebalance = portfolioValueBeforeRebalance;
        this.portfolioValueAfterRebalance = portfolioValueAfterRebalance;
        this.totalTransactionCost = totalTransactionCost;
        this.holdingWeightsAfter = holdingWeightsAfter;
    }

    public LocalDate getRebalanceDate() {
        return rebalanceDate;
    }

    public List<MomentumFormationScore> getRankedScores() {
        return rankedScores;
    }

    public List<MomentumDataQualityExclusion> getExcludedTickers() {
        return excludedTickers;
    }

    public List<String> getEnteredTickers() {
        return enteredTickers;
    }

    public List<String> getRetainedTickers() {
        return retainedTickers;
    }

    public List<String> getExitedTickers() {
        return exitedTickers;
    }

    public BigDecimal getPortfolioValueBeforeRebalance() {
        return portfolioValueBeforeRebalance;
    }

    public BigDecimal getPortfolioValueAfterRebalance() {
        return portfolioValueAfterRebalance;
    }

    public BigDecimal getTotalTransactionCost() {
        return totalTransactionCost;
    }

    public Map<String, BigDecimal> getHoldingWeightsAfter() {
        return holdingWeightsAfter;
    }
}
