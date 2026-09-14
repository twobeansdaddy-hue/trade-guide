package com.tradeguide.service.backtest;

import com.tradeguide.domain.backtest.BacktestAssumptions;
import com.tradeguide.domain.backtest.CrossSectionalMomentumBacktestResult;
import com.tradeguide.domain.backtest.CrossSectionalMomentumRebalanceEvent;
import com.tradeguide.domain.backtest.MomentumDataQualityExclusion;
import com.tradeguide.domain.backtest.MomentumDataQualityReason;
import com.tradeguide.domain.backtest.MomentumFormationScore;
import com.tradeguide.domain.backtest.MomentumTierSelectionResult;
import com.tradeguide.domain.market.MarketCandle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Track B 횡단면 모멘텀 후보를 검증하기 위한 순수 계산 백테스트 엔진이다.
 *
 * 외부 시세 API를 호출하지 않으며, DB에 쓰지 않고, Track B 실행 엔진
 * ({@code TradingStrategy}/{@code StrategySelector})이나 API·화면과
 * 연결되지 않는다. 이 클래스가 계산한 결과는 전략 채택을 의미하지 않는다
 * (자세한 한계는 {@code research/reports/track-b-cross-sectional-momentum-validation.md}
 * 참고).
 *
 * 형성기간 수익률 계산({@link MomentumFormationReturnCalculator}), 리밸런싱
 * 시점 계산({@link MomentumRebalanceScheduler}), 상위 후보 선택
 * ({@link MomentumTopCandidateSelector})을 조합해 동일가중 포트폴리오
 * 수익을 계산한다. 각 리밸런싱 시점에서는 그 시점까지의 캔들만 사용하므로
 * 미래 시점 가격이 과거 순위에 영향을 주지 않는다.
 *
 * 리밸런싱 사이 구간의 주간 시가평가(mark-to-market)와 최대낙폭은 계산하지
 * 않는다 — 이 엔진은 형성기간 수익률·순위·리밸런싱 시점·비용 반영이
 * 올바른지 검증하는 용도이며, 실데이터 기반 실전 지표(샤프비율, MDD 등)는
 * 별도 조사 단계(연구 보고서의 "다음 단계" 참고)에서 계산해야 한다.
 */
@Component
public class CrossSectionalMomentumBacktestEngine {

    private static final int CALCULATION_SCALE = 10;

    private final MomentumFormationReturnCalculator formationReturnCalculator;
    private final MomentumRebalanceScheduler rebalanceScheduler;
    private final MomentumTopCandidateSelector topCandidateSelector;

    public CrossSectionalMomentumBacktestEngine(
            MomentumFormationReturnCalculator formationReturnCalculator,
            MomentumRebalanceScheduler rebalanceScheduler,
            MomentumTopCandidateSelector topCandidateSelector
    ) {
        this.formationReturnCalculator = formationReturnCalculator;
        this.rebalanceScheduler = rebalanceScheduler;
        this.topCandidateSelector = topCandidateSelector;
    }

    /**
     * @param universeWeeklyCandles 종목별 완료 주봉 캔들 이력(시점 순서 보장, 티커 -> 캔들 목록)
     * @param initialCash           초기 현금
     * @param rebalanceIntervalWeeks 리밸런싱 주기(주). 숨겨진 기본값 없음 — 호출자가 명시한다.
     * @param topTierFraction       신규 진입 상한 비율(상위 몇 %까지 신규 편입을 허용하는지)
     * @param holdTierFraction      기존 보유 유지 하한 비율(이 비율 밖으로 밀려나야 이탈)
     * @param assumptions           수수료·슬리피지 가정. 0 비용을 기본값으로 숨기지 않으므로 항상 명시적으로 전달해야 한다.
     * @param pointInTimeUniverse   입력 유니버스가 시점별(point-in-time) 구성이면 true.
     *                              false면 생존 편향이 있을 수 있다는 뜻이며, 이 값 자체가 편향을 해결하지 않는다.
     */
    public CrossSectionalMomentumBacktestResult run(
            Map<String, List<MarketCandle>> universeWeeklyCandles,
            BigDecimal initialCash,
            int rebalanceIntervalWeeks,
            BigDecimal topTierFraction,
            BigDecimal holdTierFraction,
            BacktestAssumptions assumptions,
            boolean pointInTimeUniverse
    ) {
        if (universeWeeklyCandles == null || universeWeeklyCandles.isEmpty()) {
            throw new IllegalArgumentException("횡단면 모멘텀 백테스트에는 최소 1개 종목 이상의 캔들 이력이 필요합니다.");
        }

        if (initialCash == null || initialCash.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("백테스트 초기 자산은 0보다 커야 합니다.");
        }

        if (assumptions == null || assumptions.getFeeRate() == null || assumptions.getSlippageRate() == null) {
            throw new IllegalArgumentException("거래비용·슬리피지 가정을 명시적으로 전달해야 합니다.");
        }

        Map<String, List<MarketCandle>> validUniverse = new TreeMap<>();
        List<MomentumDataQualityExclusion> universeExclusions = new ArrayList<>();

        for (Map.Entry<String, List<MarketCandle>> entry : new TreeMap<>(universeWeeklyCandles).entrySet()) {
            String ticker = entry.getKey();
            List<MarketCandle> candles = entry.getValue();

            if (candles == null || candles.isEmpty()) {
                universeExclusions.add(new MomentumDataQualityExclusion(
                        ticker, null, MomentumDataQualityReason.MISSING_HISTORY, "캔들 이력이 비어 있습니다."
                ));
                continue;
            }

            if (!isSortedAscendingWithoutDuplicates(candles)) {
                MomentumDataQualityReason reason = hasDuplicateDates(candles)
                        ? MomentumDataQualityReason.DUPLICATE_TRADING_DATE
                        : MomentumDataQualityReason.UNSORTED_HISTORY;
                universeExclusions.add(new MomentumDataQualityExclusion(
                        ticker, null, reason,
                        reason == MomentumDataQualityReason.DUPLICATE_TRADING_DATE
                                ? "동일한 거래일이 중복돼 있습니다."
                                : "캔들 이력이 거래일 오름차순으로 정렬돼 있지 않습니다."
                ));
                continue;
            }

            validUniverse.put(ticker, candles);
        }

        if (validUniverse.isEmpty()) {
            throw new IllegalArgumentException("유효한 종목 이력이 없어 횡단면 모멘텀 백테스트를 실행할 수 없습니다.");
        }

        TreeSet<LocalDate> calendarSet = new TreeSet<>();
        for (List<MarketCandle> candles : validUniverse.values()) {
            for (MarketCandle candle : candles) {
                calendarSet.add(candle.getTradingDate());
            }
        }
        List<LocalDate> calendar = new ArrayList<>(calendarSet);

        List<LocalDate> rebalanceDates = rebalanceScheduler.schedule(calendar, rebalanceIntervalWeeks);
        if (rebalanceDates.isEmpty()) {
            throw new IllegalArgumentException("형성기간(52주)을 채울 만큼 이력이 없어 리밸런싱 시점을 계산할 수 없습니다.");
        }

        Map<String, Map<LocalDate, Integer>> dateIndexByTicker = new HashMap<>();
        for (Map.Entry<String, List<MarketCandle>> entry : validUniverse.entrySet()) {
            Map<LocalDate, Integer> index = new HashMap<>();
            List<MarketCandle> candles = entry.getValue();
            for (int i = 0; i < candles.size(); i++) {
                index.put(candles.get(i).getTradingDate(), i);
            }
            dateIndexByTicker.put(entry.getKey(), index);
        }

        BigDecimal costRate = assumptions.getFeeRate().add(assumptions.getSlippageRate());

        BigDecimal cash = initialCash;
        Map<String, BigDecimal> shares = new LinkedHashMap<>();
        Set<String> holdings = new LinkedHashSet<>();
        List<CrossSectionalMomentumRebalanceEvent> events = new ArrayList<>();

        for (LocalDate rebalanceDate : rebalanceDates) {
            List<FormationCandidate> candidates = new ArrayList<>();
            List<MomentumDataQualityExclusion> perDateExclusions = new ArrayList<>();
            Map<String, BigDecimal> priceAtRebalance = new HashMap<>();

            for (Map.Entry<String, List<MarketCandle>> entry : validUniverse.entrySet()) {
                String ticker = entry.getKey();
                List<MarketCandle> candles = entry.getValue();
                Integer index = dateIndexByTicker.get(ticker).get(rebalanceDate);

                if (index == null) {
                    perDateExclusions.add(new MomentumDataQualityExclusion(
                            ticker, rebalanceDate, MomentumDataQualityReason.MISSING_HISTORY,
                            "해당 리밸런싱 시점에 캔들 데이터가 없습니다."
                    ));
                    continue;
                }

                priceAtRebalance.put(ticker, candles.get(index).getClose());

                List<MarketCandle> truncated = candles.subList(0, index + 1);
                Optional<BigDecimal> formationReturn = formationReturnCalculator.calculate(truncated);

                if (formationReturn.isEmpty()) {
                    perDateExclusions.add(new MomentumDataQualityExclusion(
                            ticker, rebalanceDate, MomentumDataQualityReason.INSUFFICIENT_HISTORY,
                            "52주 형성기간을 채울 이력이 이 시점까지 부족합니다."
                    ));
                    continue;
                }

                candidates.add(new FormationCandidate(ticker, formationReturn.get()));
            }

            candidates.sort((a, b) -> {
                int comparison = b.returnRate.compareTo(a.returnRate);
                return comparison != 0 ? comparison : a.ticker.compareTo(b.ticker);
            });

            List<MomentumFormationScore> rankedScores = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                FormationCandidate candidate = candidates.get(i);
                rankedScores.add(new MomentumFormationScore(candidate.ticker, rebalanceDate, candidate.returnRate, i + 1));
            }

            Set<String> heldWithPrice = new LinkedHashSet<>();
            Set<String> heldWithoutPrice = new LinkedHashSet<>();
            for (String ticker : holdings) {
                if (priceAtRebalance.containsKey(ticker)) {
                    heldWithPrice.add(ticker);
                } else {
                    heldWithoutPrice.add(ticker);
                    perDateExclusions.add(new MomentumDataQualityExclusion(
                            ticker, rebalanceDate, MomentumDataQualityReason.MISSING_HISTORY,
                            "보유 중이지만 이 시점에 가격이 없어 매매하지 않고 이월합니다."
                    ));
                }
            }

            MomentumTierSelectionResult selection = topCandidateSelector.select(
                    rankedScores, heldWithPrice, topTierFraction, holdTierFraction
            );

            BigDecimal portfolioValueBeforeRebalance = cash;
            for (String ticker : heldWithPrice) {
                portfolioValueBeforeRebalance = portfolioValueBeforeRebalance.add(
                        shares.getOrDefault(ticker, BigDecimal.ZERO).multiply(priceAtRebalance.get(ticker))
                );
            }

            int tradeableHoldingsCount = selection.getNewHoldings().size();
            BigDecimal targetValuePerTicker = tradeableHoldingsCount > 0
                    ? portfolioValueBeforeRebalance.divide(BigDecimal.valueOf(tradeableHoldingsCount), CALCULATION_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Set<String> tradeUniverse = new LinkedHashSet<>(heldWithPrice);
            tradeUniverse.addAll(selection.getNewHoldings());

            BigDecimal totalTransactionCost = BigDecimal.ZERO;
            Map<String, BigDecimal> holdingWeightsAfter = new LinkedHashMap<>();

            for (String ticker : tradeUniverse) {
                BigDecimal price = priceAtRebalance.get(ticker);
                BigDecimal currentShares = shares.getOrDefault(ticker, BigDecimal.ZERO);
                BigDecimal currentValue = currentShares.multiply(price);
                BigDecimal targetValue = selection.getNewHoldings().contains(ticker) ? targetValuePerTicker : BigDecimal.ZERO;
                BigDecimal delta = targetValue.subtract(currentValue);

                if (delta.compareTo(BigDecimal.ZERO) > 0) {
                    // 매수: 목표 배분 금액 전액이 현금에서 빠져나가고, 비용만큼 매수 수량이 줄어든다.
                    BigDecimal cost = delta.multiply(costRate);
                    BigDecimal investable = delta.subtract(cost);
                    BigDecimal sharesDelta = investable.divide(price, CALCULATION_SCALE, RoundingMode.HALF_UP);
                    currentShares = currentShares.add(sharesDelta);
                    cash = cash.subtract(delta);
                    totalTransactionCost = totalTransactionCost.add(cost);
                } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
                    // 매도: 매도 수량은 목표 감소분 전액에 해당하고, 비용만큼 현금 유입이 줄어든다.
                    BigDecimal notional = delta.abs();
                    BigDecimal cost = notional.multiply(costRate);
                    BigDecimal sharesDelta = delta.divide(price, CALCULATION_SCALE, RoundingMode.HALF_UP);
                    currentShares = currentShares.add(sharesDelta);
                    cash = cash.add(notional.subtract(cost));
                    totalTransactionCost = totalTransactionCost.add(cost);
                }

                if (targetValue.compareTo(BigDecimal.ZERO) == 0) {
                    shares.remove(ticker);
                } else {
                    shares.put(ticker, currentShares);
                    holdingWeightsAfter.put(ticker, targetValuePerTicker);
                }
            }

            holdings = new LinkedHashSet<>(selection.getNewHoldings());
            holdings.addAll(heldWithoutPrice);

            BigDecimal portfolioValueAfterRebalance = cash;
            for (String ticker : selection.getNewHoldings()) {
                portfolioValueAfterRebalance = portfolioValueAfterRebalance.add(
                        shares.getOrDefault(ticker, BigDecimal.ZERO).multiply(priceAtRebalance.get(ticker))
                );
            }

            events.add(new CrossSectionalMomentumRebalanceEvent(
                    rebalanceDate,
                    rankedScores,
                    perDateExclusions,
                    selection.getEnteredTickers(),
                    selection.getRetainedTickers(),
                    selection.getExitedTickers(),
                    portfolioValueBeforeRebalance,
                    portfolioValueAfterRebalance,
                    totalTransactionCost,
                    holdingWeightsAfter
            ));
        }

        BigDecimal endingPortfolioValue = cash;
        for (String ticker : holdings) {
            List<MarketCandle> candles = validUniverse.get(ticker);
            if (candles == null || !shares.containsKey(ticker)) {
                continue;
            }
            BigDecimal lastKnownPrice = candles.get(candles.size() - 1).getClose();
            endingPortfolioValue = endingPortfolioValue.add(shares.get(ticker).multiply(lastKnownPrice));
        }

        BigDecimal cumulativeReturnRate = endingPortfolioValue.subtract(initialCash)
                .multiply(BigDecimal.valueOf(100))
                .divide(initialCash, CALCULATION_SCALE, RoundingMode.HALF_UP);

        return new CrossSectionalMomentumBacktestResult(
                rebalanceDates.get(0),
                calendar.get(calendar.size() - 1),
                initialCash,
                endingPortfolioValue,
                cumulativeReturnRate,
                events,
                universeExclusions,
                assumptions,
                pointInTimeUniverse,
                rebalanceIntervalWeeks,
                topTierFraction,
                holdTierFraction
        );
    }

    private boolean hasDuplicateDates(List<MarketCandle> candles) {
        Set<LocalDate> seen = new TreeSet<>();
        for (MarketCandle candle : candles) {
            if (!seen.add(candle.getTradingDate())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSortedAscendingWithoutDuplicates(List<MarketCandle> candles) {
        for (int i = 1; i < candles.size(); i++) {
            if (!candles.get(i).getTradingDate().isAfter(candles.get(i - 1).getTradingDate())) {
                return false;
            }
        }
        return true;
    }

    private static final class FormationCandidate {
        private final String ticker;
        private final BigDecimal returnRate;

        private FormationCandidate(String ticker, BigDecimal returnRate) {
            this.ticker = ticker;
            this.returnRate = returnRate;
        }
    }
}
