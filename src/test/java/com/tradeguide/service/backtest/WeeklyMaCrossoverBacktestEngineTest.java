package com.tradeguide.service.backtest;

import com.tradeguide.domain.backtest.BacktestResult;
import com.tradeguide.domain.backtest.BacktestTradeEvent;
import com.tradeguide.domain.backtest.BacktestTradeType;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.service.indicator.SimpleMovingAverageCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WeeklyMaCrossoverBacktestEngineTest {

    private final WeeklyMaCrossoverBacktestEngine engine =
            new WeeklyMaCrossoverBacktestEngine(new SimpleMovingAverageCalculator());

    @Test
    void executesBuyOnCrossUpAndSellOnCrossDown() {
        // Given: 100 -> 200 골든크로스(index 40) 이후 유지하다가 50으로 급락(index 100)해 데드크로스가 발생하는 41주 이상의 완료 주봉
        List<MarketCandle> candles = crossUpThenCrossDownCandles();

        // When
        BacktestResult result = engine.run(candles, new BigDecimal("1000"));

        // Then
        assertThat(result.getTradeCount()).isEqualTo(2);

        BacktestTradeEvent buy = result.getTrades().get(0);
        assertThat(buy.getType()).isEqualTo(BacktestTradeType.BUY);
        assertThat(buy.getTradingDate()).isEqualTo(candles.get(40).getTradingDate());
        assertThat(buy.getPrice()).isEqualByComparingTo("200");
        assertThat(buy.getQuantity()).isEqualByComparingTo("5");
        assertThat(buy.getCashAfter()).isEqualByComparingTo("0");

        BacktestTradeEvent sell = result.getTrades().get(1);
        assertThat(sell.getType()).isEqualTo(BacktestTradeType.SELL);
        assertThat(sell.getTradingDate()).isEqualTo(candles.get(100).getTradingDate());
        assertThat(sell.getPrice()).isEqualByComparingTo("50");
        assertThat(sell.getQuantity()).isEqualByComparingTo("5");
        assertThat(sell.getCashAfter()).isEqualByComparingTo("250");

        assertThat(result.getPeriodStart()).isEqualTo(candles.get(40).getTradingDate());
        assertThat(result.getPeriodEnd()).isEqualTo(candles.get(100).getTradingDate());
        assertThat(result.getStartingPortfolioValue()).isEqualByComparingTo("1000");
        assertThat(result.getEndingPortfolioValue()).isEqualByComparingTo("250");
        assertThat(result.getCumulativeReturnRate()).isEqualByComparingTo("-75");

        assertThat(result.getAssumptions().getFeeRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getAssumptions().getSlippageRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void capturesMaxDrawdownAfterPortfolioPeakDeclines() {
        // Given: 매수 시점(index 40)에서 자산가치 1000을 찍은 뒤 매도 시점(index 100)에 250까지 하락
        List<MarketCandle> candles = crossUpThenCrossDownCandles();

        // When
        BacktestResult result = engine.run(candles, new BigDecimal("1000"));

        // Then: (1000 - 250) / 1000 = 75%
        assertThat(result.getMaxDrawdownRate()).isEqualByComparingTo("75");
    }

    @Test
    void throwsExceptionWhenCandlesAreFewerThanFortyOne() {
        // Given
        List<MarketCandle> candles = flatCandles(40, "100");

        // When & Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> engine.run(candles, new BigDecimal("1000")))
                .withMessage("백테스트를 실행하려면 최소 41개의 완료된 주봉 캔들이 필요합니다.");
    }

    @Test
    void excludesLastIncompleteCandleFromCalculation() {
        // Given: 42주 평탄한 캔들(교차 없음) 뒤에 아직 완료되지 않은 43주차 급등 캔들이 이어진다.
        List<MarketCandle> completedCandles = flatCandles(42, "100");
        List<MarketCandle> withIncompleteTrailingCandle = appendCandle(
                completedCandles,
                completedCandles.get(completedCandles.size() - 1).getTradingDate().plusWeeks(1),
                "300"
        );

        // When: 호출자가 마지막 미완료 캔들을 제외하고 백테스트를 실행하면
        BacktestResult resultWithoutIncompleteCandle = engine.run(completedCandles, new BigDecimal("1000"));

        // Then: 교차가 발생하지 않아 매매가 없다.
        assertThat(resultWithoutIncompleteCandle.getTradeCount()).isZero();

        // When: 미완료 캔들을 실수로 포함하면
        BacktestResult resultWithIncompleteCandle = engine.run(withIncompleteTrailingCandle, new BigDecimal("1000"));

        // Then: 급등 캔들 하나만으로 매수 신호가 발생해 결과가 달라진다 -> 반드시 제외해야 한다.
        assertThat(resultWithIncompleteCandle.getTradeCount()).isEqualTo(1);
        assertThat(resultWithIncompleteCandle.getTrades().get(0).getType()).isEqualTo(BacktestTradeType.BUY);
    }

    private List<MarketCandle> crossUpThenCrossDownCandles() {
        return IntStream.rangeClosed(0, 100)
                .mapToObj(index -> {
                    String close;
                    if (index < 40) {
                        close = "100";
                    } else if (index < 100) {
                        close = "200";
                    } else {
                        close = "50";
                    }
                    return candle(LocalDate.of(2026, 1, 2).plusWeeks(index), close);
                })
                .toList();
    }

    private List<MarketCandle> flatCandles(int count, String close) {
        return IntStream.range(0, count)
                .mapToObj(index -> candle(
                        LocalDate.of(2026, 1, 2).plusWeeks(index),
                        close
                ))
                .toList();
    }

    private List<MarketCandle> appendCandle(
            List<MarketCandle> candles,
            LocalDate tradingDate,
            String close
    ) {
        List<MarketCandle> extended = new java.util.ArrayList<>(candles);
        extended.add(candle(tradingDate, close));
        return extended;
    }

    private MarketCandle candle(LocalDate tradingDate, String close) {
        BigDecimal price = new BigDecimal(close);

        return new MarketCandle(
                Market.US,
                "SOXL",
                tradingDate,
                price,
                price,
                price,
                price,
                1_000L
        );
    }
}
