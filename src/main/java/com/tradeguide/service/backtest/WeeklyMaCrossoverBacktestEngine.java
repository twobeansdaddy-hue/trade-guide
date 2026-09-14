package com.tradeguide.service.backtest;

import com.tradeguide.domain.backtest.BacktestAssumptions;
import com.tradeguide.domain.backtest.BacktestResult;
import com.tradeguide.domain.backtest.BacktestTradeEvent;
import com.tradeguide.domain.backtest.BacktestTradeType;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.service.indicator.SimpleMovingAverageCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * TRACK_A 10주/40주 이동평균 교차 전략을 과거 완료 주봉 캔들에 적용해
 * 매수/현금 포지션 전환을 재현하는 순수 백테스트 핵심이다.
 *
 * 입력 {@code candles}는 호출자가 이미 완료 처리한 주봉만 포함해야 한다.
 * 이 클래스는 시스템 시각을 참조하지 않으며, 각 인덱스의 판단은
 * 해당 인덱스까지의 캔들만 사용해 미래 데이터를 참조하지 않는다.
 * 수수료와 슬리피지는 {@link BacktestAssumptions#zeroCost()}로 고정한
 * 초기 가정이며, 목표가·손절가·실제 주문 체결은 다루지 않는다.
 */
@Component
public class WeeklyMaCrossoverBacktestEngine {

    private static final int SHORT_PERIOD = 10;
    private static final int LONG_PERIOD = 40;
    private static final int CALCULATION_SCALE = 10;

    private final SimpleMovingAverageCalculator movingAverageCalculator;

    public WeeklyMaCrossoverBacktestEngine(
            SimpleMovingAverageCalculator movingAverageCalculator
    ) {
        this.movingAverageCalculator = movingAverageCalculator;
    }

    public BacktestResult run(
            List<MarketCandle> candles,
            BigDecimal initialCash
    ) {
        if (candles == null || candles.size() <= LONG_PERIOD) {
            throw new IllegalArgumentException(
                    "백테스트를 실행하려면 최소 41개의 완료된 주봉 캔들이 필요합니다."
            );
        }

        if (initialCash == null || initialCash.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "백테스트 초기 자산은 0보다 커야 합니다."
            );
        }

        BigDecimal cash = initialCash;
        BigDecimal shares = BigDecimal.ZERO;
        List<BacktestTradeEvent> trades = new ArrayList<>();

        BigDecimal peakPortfolioValue = initialCash;
        BigDecimal maxDrawdownRate = BigDecimal.ZERO;
        BigDecimal endingPortfolioValue = initialCash;

        for (int index = LONG_PERIOD; index < candles.size(); index++) {
            MarketCandle candle = candles.get(index);
            BigDecimal price = candle.getClose();

            CrossoverEvent crossoverEvent = detectCrossoverEvent(candles, index);

            if (crossoverEvent == CrossoverEvent.CROSS_UP
                    && shares.compareTo(BigDecimal.ZERO) == 0
                    && cash.compareTo(BigDecimal.ZERO) > 0) {

                shares = cash.divide(price, CALCULATION_SCALE, RoundingMode.HALF_UP);
                cash = BigDecimal.ZERO;

                trades.add(new BacktestTradeEvent(
                        candle.getTradingDate(),
                        BacktestTradeType.BUY,
                        price,
                        shares,
                        cash,
                        shares,
                        shares.multiply(price)
                ));
            } else if (crossoverEvent == CrossoverEvent.CROSS_DOWN
                    && shares.compareTo(BigDecimal.ZERO) > 0) {

                BigDecimal sharesSold = shares;
                cash = shares.multiply(price);
                shares = BigDecimal.ZERO;

                trades.add(new BacktestTradeEvent(
                        candle.getTradingDate(),
                        BacktestTradeType.SELL,
                        price,
                        sharesSold,
                        cash,
                        shares,
                        cash
                ));
            }

            BigDecimal portfolioValue = cash.add(shares.multiply(price));
            endingPortfolioValue = portfolioValue;

            if (portfolioValue.compareTo(peakPortfolioValue) > 0) {
                peakPortfolioValue = portfolioValue;
            }

            BigDecimal drawdownRate = peakPortfolioValue.subtract(portfolioValue)
                    .divide(peakPortfolioValue, CALCULATION_SCALE, RoundingMode.HALF_UP);

            if (drawdownRate.compareTo(maxDrawdownRate) > 0) {
                maxDrawdownRate = drawdownRate;
            }
        }

        BigDecimal cumulativeReturnRate = endingPortfolioValue.subtract(initialCash)
                .multiply(BigDecimal.valueOf(100))
                .divide(initialCash, CALCULATION_SCALE, RoundingMode.HALF_UP);

        return new BacktestResult(
                candles.get(LONG_PERIOD).getTradingDate(),
                candles.get(candles.size() - 1).getTradingDate(),
                initialCash,
                endingPortfolioValue,
                cumulativeReturnRate,
                maxDrawdownRate.multiply(BigDecimal.valueOf(100)),
                trades.size(),
                trades,
                BacktestAssumptions.zeroCost()
        );
    }

    private CrossoverEvent detectCrossoverEvent(
            List<MarketCandle> candles,
            int currentIndex
    ) {
        List<MarketCandle> previousCandles = candles.subList(0, currentIndex);
        List<MarketCandle> currentCandles = candles.subList(0, currentIndex + 1);

        BigDecimal previousShortAverage = movingAverageCalculator.calculate(previousCandles, SHORT_PERIOD);
        BigDecimal previousLongAverage = movingAverageCalculator.calculate(previousCandles, LONG_PERIOD);
        BigDecimal currentShortAverage = movingAverageCalculator.calculate(currentCandles, SHORT_PERIOD);
        BigDecimal currentLongAverage = movingAverageCalculator.calculate(currentCandles, LONG_PERIOD);

        if (previousShortAverage.compareTo(previousLongAverage) <= 0
                && currentShortAverage.compareTo(currentLongAverage) > 0) {
            return CrossoverEvent.CROSS_UP;
        }

        if (previousShortAverage.compareTo(previousLongAverage) >= 0
                && currentShortAverage.compareTo(currentLongAverage) < 0) {
            return CrossoverEvent.CROSS_DOWN;
        }

        return CrossoverEvent.NONE;
    }

    private enum CrossoverEvent {
        CROSS_UP,
        CROSS_DOWN,
        NONE
    }
}
