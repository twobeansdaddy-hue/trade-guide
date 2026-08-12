package com.tradeguide.service.strategy.tracka;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.*;
import com.tradeguide.service.indicator.SimpleMovingAverageCalculator;
import com.tradeguide.service.strategy.TradingStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class WeeklyMaCrossoverStrategy implements TradingStrategy {

    private static final int SHORT_PERIOD = 10;
    private static final int LONG_PERIOD = 40;
    private static final String STRATEGY_ID = "track-a-weekly-ma-crossover";
    private static final String STRATEGY_VERSION = "v1";

    private final SimpleMovingAverageCalculator movingAverageCalculator;

    public WeeklyMaCrossoverStrategy(
            SimpleMovingAverageCalculator movingAverageCalculator
    ) {
        this.movingAverageCalculator = movingAverageCalculator;
    }

    @Override
    public boolean supports(InvestmentTrack investmentTrack) {
        return investmentTrack == InvestmentTrack.TRACK_A;
    }

    @Override
    public StrategySignal decide(
            AssetProfile assetProfile,
            List<MarketCandle> candles
    ) {
        if (assetProfile.getInvestmentTrack() != InvestmentTrack.TRACK_A) {
            throw new IllegalArgumentException(
                    "TRACK_A 종목에서만 사용할 수 있는 전략입니다."
            );
        }

        if (candles.size() <= LONG_PERIOD) {
            throw new IllegalArgumentException(
                    "주봉 이동평균 교차 전략에는 최소 41개의 캔들이 필요합니다."
            );
        }

        MarketCandle lastestCandle = candles.get(candles.size() - 1);
        StrategyTrend trend = determineTrend(candles);
        StrategySignalEvent signalEvent = determineSignalEvent(candles, candles.size() - 1);

        return new StrategySignal(
                lastestCandle.getClose(),
                createReason(trend, signalEvent),
                new StrategyMetadata(
                        STRATEGY_ID,
                        STRATEGY_VERSION,
                        lastestCandle.getTradingDate()
                ),
                trend,
                signalEvent,
                findWeeksSinceCross(candles)
        );
    }

    private StrategyTrend determineTrend(List<MarketCandle> candles) {


        BigDecimal shortAverage = movingAverageCalculator.calculate(candles, SHORT_PERIOD);
        BigDecimal longAverage = movingAverageCalculator.calculate(candles, LONG_PERIOD);

        if (shortAverage.compareTo(longAverage) > 0) {
            return StrategyTrend.ABOVE_LONG_AVERAGE;
        }

        return StrategyTrend.BELOW_LONG_AVERAGE;

    }

    private StrategySignalEvent determineSignalEvent(
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
            return StrategySignalEvent.CROSS_UP;
        }

        if (previousShortAverage.compareTo(previousLongAverage) >= 0
                && currentShortAverage.compareTo(currentLongAverage) < 0) {
            return StrategySignalEvent.CROSS_DOWN;
        }

        return StrategySignalEvent.NONE;
    }

    private Integer findWeeksSinceCross(List<MarketCandle> candles) {
        int latestIndex = candles.size() - 1;

        for (int currentIndex = latestIndex; currentIndex >= LONG_PERIOD; currentIndex--) {

            StrategySignalEvent signalEvent = determineSignalEvent(candles, currentIndex);

            if (signalEvent != StrategySignalEvent.NONE) {
                return latestIndex - currentIndex;
            }
        }

        return null;
    }

    private String createReason(
            StrategyTrend trend,
            StrategySignalEvent signalEvent
    ) {
        if (signalEvent == StrategySignalEvent.CROSS_UP) {
            return "10주 이동평균이 40주 이동평균을 상향 돌파했습니다.";
        }

        if (signalEvent == StrategySignalEvent.CROSS_DOWN) {
            return "10주 이동평균이 40주 이동평균을 하향 돌파했습니다.";
        }

        if (trend == StrategyTrend.ABOVE_LONG_AVERAGE) {
            return "10주 이동평균이 40주 이동평균 위에 있어 상승 추세가 유지되고 있습니다.";
        }

        return "10주 이동평균이 40주 이동평균 아래에 있어 하락 추세가 유지되고 있습니다.";
    }
}
