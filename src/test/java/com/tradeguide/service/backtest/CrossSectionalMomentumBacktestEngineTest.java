package com.tradeguide.service.backtest;

import com.tradeguide.domain.backtest.BacktestAssumptions;
import com.tradeguide.domain.backtest.CrossSectionalMomentumBacktestResult;
import com.tradeguide.domain.backtest.CrossSectionalMomentumRebalanceEvent;
import com.tradeguide.domain.backtest.MomentumDataQualityReason;
import com.tradeguide.domain.backtest.MomentumFormationScore;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CrossSectionalMomentumBacktestEngineTest {

    private static final LocalDate CALENDAR_START = LocalDate.of(2020, 1, 3);
    private static final BigDecimal TOP_TIER_FRACTION = new BigDecimal("0.2");
    private static final BigDecimal HOLD_TIER_FRACTION = new BigDecimal("0.6");

    private final CrossSectionalMomentumBacktestEngine engine = new CrossSectionalMomentumBacktestEngine(
            new MomentumFormationReturnCalculator(),
            new MomentumRebalanceScheduler(),
            new MomentumTopCandidateSelector()
    );

    @Test
    void ranksFormationReturnsCorrectlyAndBuysTopTierWithEqualWeight() {
        // Given: 5종목, 53주(첫 리밸런싱 1회분). 52주 전 종가 100 공통, 최근 4주(및 리밸런싱 당일) 종가로 형성기간 수익률을 다르게 만든다.
        Map<String, List<MarketCandle>> universe = fiveTickerUniverse(53);

        // When
        CrossSectionalMomentumBacktestResult result = engine.run(
                universe, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                BacktestAssumptions.zeroCost(), false
        );

        // Then
        assertThat(result.getRebalanceEvents()).hasSize(1);
        CrossSectionalMomentumRebalanceEvent event = result.getRebalanceEvents().get(0);

        List<MomentumFormationScore> scores = event.getRankedScores();
        assertThat(scores).extracting(MomentumFormationScore::getTicker)
                .containsExactly("AAA", "BBB", "CCC", "DDD", "EEE");
        assertThat(scores.get(0).getFormationReturnRate()).isEqualByComparingTo("50");
        assertThat(scores.get(1).getFormationReturnRate()).isEqualByComparingTo("30");
        assertThat(scores.get(2).getFormationReturnRate()).isEqualByComparingTo("10");
        assertThat(scores.get(3).getFormationReturnRate()).isEqualByComparingTo("-10");
        assertThat(scores.get(4).getFormationReturnRate()).isEqualByComparingTo("-30");
        assertThat(scores).extracting(MomentumFormationScore::getRank).containsExactly(1, 2, 3, 4, 5);

        // Then: 상위 20%(1종목)만 신규 진입 — 동일가중이므로 1종목이면 전액 투입
        assertThat(event.getEnteredTickers()).containsExactly("AAA");
        assertThat(event.getRetainedTickers()).isEmpty();
        assertThat(event.getExitedTickers()).isEmpty();

        // Then: 무비용 가정이므로 15000 / 150(AAA 리밸런싱 당일 종가) = 100주, 잔여 현금 0
        assertThat(result.getEndingPortfolioValue()).isEqualByComparingTo("15000");
        assertThat(result.getCumulativeReturnRate()).isEqualByComparingTo("0");
        assertThat(event.getTotalTransactionCost()).isEqualByComparingTo("0");
    }

    @Test
    void compositionDoesNotChangeUntilNextScheduledRebalance() {
        // Given: 53주 데이터만 존재해 리밸런싱은 한 번만 발생한다.
        // rebalanceIntervalWeeks를 늘려도(예: 13주) 총 이력이 53주뿐이므로 두 번째 리밸런싱은 발생하지 않는다.
        Map<String, List<MarketCandle>> universe = fiveTickerUniverse(53);

        // When
        CrossSectionalMomentumBacktestResult result = engine.run(
                universe, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                BacktestAssumptions.zeroCost(), false
        );

        // Then: 리밸런싱은 한 번뿐이고, 최초 리밸런싱에서 편입된 AAA만 만기까지 보유한다.
        assertThat(result.getRebalanceEvents()).hasSize(1);
        assertThat(result.getRebalanceEvents().get(0).getEnteredTickers()).containsExactly("AAA");

        // Then: 만기 자산가치는 AAA 100주 x AAA 마지막 종가(150)로만 결정된다 — 다른 종목 가격 변화는 영향을 주지 않는다.
        assertThat(result.getEndingPortfolioValue()).isEqualByComparingTo("15000");
    }

    @Test
    void costAssumptionNeverProducesHigherEndingValueThanZeroCost() {
        // Given: 동일한 유니버스, 동일한 정책 — 비용 가정만 다르게 실행
        Map<String, List<MarketCandle>> universe = fiveTickerUniverse(53);
        BacktestAssumptions withCost = new BacktestAssumptions(new BigDecimal("0.001"), new BigDecimal("0.0015"));

        // When
        CrossSectionalMomentumBacktestResult zeroCostResult = engine.run(
                universe, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                BacktestAssumptions.zeroCost(), false
        );
        CrossSectionalMomentumBacktestResult withCostResult = engine.run(
                universe, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                withCost, false
        );

        // Then: 비용이 있는 결과가 무비용 결과보다 높을 수 없다 (이 표본에서는 매수 비용만큼 엄격히 더 낮다)
        assertThat(withCostResult.getEndingPortfolioValue())
                .isLessThanOrEqualTo(zeroCostResult.getEndingPortfolioValue());
        assertThat(withCostResult.getEndingPortfolioValue()).isEqualByComparingTo("14962.5");
        assertThat(withCostResult.getRebalanceEvents().get(0).getTotalTransactionCost())
                .isEqualByComparingTo("37.5");
    }

    @Test
    void futureCandlesAfterFirstRebalanceDoNotAffectItsRanking() {
        // Given: 53주(첫 리밸런싱까지만)와, 동일한 53주 뒤에 두 번째 리밸런싱용 13주를 추가로 이어붙인 66주 버전
        Map<String, List<MarketCandle>> shortUniverse = fiveTickerUniverse(53);
        Map<String, List<MarketCandle>> extendedUniverse = extendWithFutureWeeks(shortUniverse, 13, "999");

        // When
        CrossSectionalMomentumBacktestResult shortResult = engine.run(
                shortUniverse, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                BacktestAssumptions.zeroCost(), false
        );
        CrossSectionalMomentumBacktestResult extendedResult = engine.run(
                extendedUniverse, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                BacktestAssumptions.zeroCost(), false
        );

        // Then: 첫 리밸런싱(같은 날짜)의 순위·수익률은 이후에 어떤 미래 데이터가 붙든 동일해야 한다.
        CrossSectionalMomentumRebalanceEvent firstFromShort = shortResult.getRebalanceEvents().get(0);
        CrossSectionalMomentumRebalanceEvent firstFromExtended = extendedResult.getRebalanceEvents().get(0);

        assertThat(firstFromExtended.getRebalanceDate()).isEqualTo(firstFromShort.getRebalanceDate());
        assertThat(firstFromExtended.getRankedScores())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(firstFromShort.getRankedScores());
        assertThat(firstFromExtended.getEnteredTickers()).isEqualTo(firstFromShort.getEnteredTickers());

        // Then: 확장판에는 두 번째 리밸런싱이 추가로 존재한다.
        assertThat(extendedResult.getRebalanceEvents()).hasSize(2);
    }

    @Test
    void excludesTickerWithDuplicateTradingDateFromEveryRebalance() {
        // Given: 5종목 유니버스에 중복 거래일을 가진 종목(FFF)을 추가한다.
        Map<String, List<MarketCandle>> universe = fiveTickerUniverse(53);
        List<MarketCandle> duplicated = new ArrayList<>(trendCandles("FFF", 53, "100", "500"));
        duplicated.set(10, candle("FFF", duplicated.get(11).getTradingDate(), "999"));
        universe.put("FFF", duplicated);

        // When
        CrossSectionalMomentumBacktestResult result = engine.run(
                universe, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                BacktestAssumptions.zeroCost(), false
        );

        // Then: FFF는 전역 제외 목록에 중복 거래일 사유로 기록되고, 어떤 리밸런싱 순위에도 등장하지 않는다.
        assertThat(result.getUniverseExclusions())
                .anyMatch(exclusion -> exclusion.getTicker().equals("FFF")
                        && exclusion.getReason() == MomentumDataQualityReason.DUPLICATE_TRADING_DATE
                        && exclusion.getRebalanceDate() == null);

        boolean fffAppearsInAnyRanking = result.getRebalanceEvents().stream()
                .flatMap(event -> event.getRankedScores().stream())
                .anyMatch(score -> score.getTicker().equals("FFF"));
        assertThat(fffAppearsInAnyRanking).isFalse();
    }

    @Test
    void excludesLateStartingTickerOnlyAtRebalancesWithInsufficientHistory() {
        // Given: GGG는 다른 종목보다 13주 늦게 시작해(총 40주) 첫 리밸런싱(52주차) 시점에는 형성기간을 채우지 못한다.
        Map<String, List<MarketCandle>> universe = fiveTickerUniverse(66);
        universe.put("GGG", lateStartingCandles("GGG", 13, 66 - 13, "200"));

        // When
        CrossSectionalMomentumBacktestResult result = engine.run(
                universe, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                BacktestAssumptions.zeroCost(), false
        );

        // Then: 첫 리밸런싱에서는 이력 부족으로 제외되고, 두 번째 리밸런싱에서는 이력이 쌓여 순위에 포함된다.
        assertThat(result.getRebalanceEvents()).hasSize(2);
        CrossSectionalMomentumRebalanceEvent firstEvent = result.getRebalanceEvents().get(0);
        CrossSectionalMomentumRebalanceEvent secondEvent = result.getRebalanceEvents().get(1);

        assertThat(firstEvent.getExcludedTickers())
                .anyMatch(exclusion -> exclusion.getTicker().equals("GGG")
                        && exclusion.getReason() == MomentumDataQualityReason.INSUFFICIENT_HISTORY);
        assertThat(firstEvent.getRankedScores()).noneMatch(score -> score.getTicker().equals("GGG"));

        assertThat(secondEvent.getRankedScores()).anyMatch(score -> score.getTicker().equals("GGG"));
    }

    @Test
    void excludesTickerMissingExactlyAtARebalanceDate() {
        // Given: HHH는 마지막 1주(리밸런싱 당일)의 캔들이 아예 없다.
        Map<String, List<MarketCandle>> universe = fiveTickerUniverse(53);
        List<MarketCandle> withoutLastWeek = trendCandles("HHH", 53, "100", "200")
                .subList(0, 52);
        universe.put("HHH", new ArrayList<>(withoutLastWeek));

        // When
        CrossSectionalMomentumBacktestResult result = engine.run(
                universe, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                BacktestAssumptions.zeroCost(), false
        );

        // Then
        CrossSectionalMomentumRebalanceEvent event = result.getRebalanceEvents().get(0);
        assertThat(event.getExcludedTickers())
                .anyMatch(exclusion -> exclusion.getTicker().equals("HHH")
                        && exclusion.getReason() == MomentumDataQualityReason.MISSING_HISTORY);
        assertThat(event.getRankedScores()).noneMatch(score -> score.getTicker().equals("HHH"));
    }

    @Test
    void throwsWhenUniverseIsEmpty() {
        assertThatIllegalArgumentException().isThrownBy(() -> engine.run(
                Map.of(), new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                BacktestAssumptions.zeroCost(), false
        ));
    }

    @Test
    void throwsWhenAssumptionsAreNotProvided() {
        Map<String, List<MarketCandle>> universe = fiveTickerUniverse(53);

        assertThatIllegalArgumentException().isThrownBy(() -> engine.run(
                universe, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                null, false
        ));
    }

    @Test
    void throwsWhenAllTickersAreExcludedByDataQuality() {
        List<MarketCandle> duplicated = new ArrayList<>(trendCandles("ONLY", 53, "100", "200"));
        duplicated.set(1, candle("ONLY", duplicated.get(2).getTradingDate(), "999"));
        Map<String, List<MarketCandle>> universe = new LinkedHashMap<>();
        universe.put("ONLY", duplicated);

        assertThatIllegalArgumentException().isThrownBy(() -> engine.run(
                universe, new BigDecimal("15000"), 13, TOP_TIER_FRACTION, HOLD_TIER_FRACTION,
                BacktestAssumptions.zeroCost(), false
        ));
    }

    /**
     * AAA~EEE 5종목, 52주 전(초기 구간) 종가는 100으로 동일하고 최근 4주(및 리밸런싱 당일) 종가만
     * 달라 형성기간 수익률이 AAA(+50%) > BBB(+30%) > CCC(+10%) > DDD(-10%) > EEE(-30%) 순이 되도록 구성한다.
     */
    private Map<String, List<MarketCandle>> fiveTickerUniverse(int weeks) {
        Map<String, List<MarketCandle>> universe = new LinkedHashMap<>();
        universe.put("AAA", trendCandles("AAA", weeks, "100", "150"));
        universe.put("BBB", trendCandles("BBB", weeks, "100", "130"));
        universe.put("CCC", trendCandles("CCC", weeks, "100", "110"));
        universe.put("DDD", trendCandles("DDD", weeks, "100", "90"));
        universe.put("EEE", trendCandles("EEE", weeks, "100", "70"));
        return universe;
    }

    private List<MarketCandle> trendCandles(String ticker, int weeks, String earlyClose, String lateClose) {
        List<MarketCandle> candles = new ArrayList<>();
        int lateStartIndex = weeks - 5;
        for (int i = 0; i < weeks; i++) {
            String close = i < lateStartIndex ? earlyClose : lateClose;
            candles.add(candle(ticker, CALENDAR_START.plusWeeks(i), close));
        }
        return candles;
    }

    private List<MarketCandle> lateStartingCandles(String ticker, int skipFirstWeeks, int weekCount, String close) {
        List<MarketCandle> candles = new ArrayList<>();
        for (int i = 0; i < weekCount; i++) {
            candles.add(candle(ticker, CALENDAR_START.plusWeeks(skipFirstWeeks + i), close));
        }
        return candles;
    }

    private Map<String, List<MarketCandle>> extendWithFutureWeeks(
            Map<String, List<MarketCandle>> base,
            int extraWeeks,
            String extraClose
    ) {
        Map<String, List<MarketCandle>> extended = new LinkedHashMap<>();
        for (Map.Entry<String, List<MarketCandle>> entry : base.entrySet()) {
            List<MarketCandle> candles = new ArrayList<>(entry.getValue());
            LocalDate nextDate = candles.get(candles.size() - 1).getTradingDate().plusWeeks(1);
            for (int i = 0; i < extraWeeks; i++) {
                candles.add(candle(entry.getKey(), nextDate.plusWeeks(i), extraClose));
            }
            extended.put(entry.getKey(), candles);
        }
        return extended;
    }

    private MarketCandle candle(String ticker, LocalDate tradingDate, String close) {
        BigDecimal price = new BigDecimal(close);
        return new MarketCandle(Market.US, ticker, tradingDate, price, price, price, price, 1_000L);
    }
}
