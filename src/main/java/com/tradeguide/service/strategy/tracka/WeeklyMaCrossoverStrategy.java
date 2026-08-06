package com.tradeguide.service.strategy.tracka;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.service.indicator.SimpleMovingAverageCalculator;
import com.tradeguide.service.strategy.TradingStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class WeeklyMaCrossoverStrategy implements TradingStrategy {

    private static final int SHORT_PERIOD = 10;
    private static final int LONG_PERIOD = 40;

    private final SimpleMovingAverageCalculator movingAverageCalculator;

    public WeeklyMaCrossoverStrategy(
            SimpleMovingAverageCalculator movingAverageCalculator
    ) {
        this.movingAverageCalculator = movingAverageCalculator;
    }

    @Override
    public StrategyDecision decide(
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

        List<MarketCandle> previousCandles = candles.subList(0, candles.size() - 1);

        BigDecimal previousShortAverage = movingAverageCalculator.calculate(previousCandles, SHORT_PERIOD);
        BigDecimal previousLongAverage = movingAverageCalculator.calculate(previousCandles, LONG_PERIOD);
        BigDecimal currentShortAverage = movingAverageCalculator.calculate(candles, SHORT_PERIOD);
        BigDecimal currentLongAverage = movingAverageCalculator.calculate(candles, LONG_PERIOD);

        BigDecimal referencePrice = candles.get(candles.size() -1).getClose();

        boolean crossedAbove = previousShortAverage.compareTo(previousLongAverage) <= 0
                && currentShortAverage.compareTo(currentLongAverage) > 0;

        boolean crossedBelow =
                previousShortAverage.compareTo(previousLongAverage) >= 0
                        && currentShortAverage.compareTo(currentLongAverage) < 0;

        if (crossedAbove) {
            return new StrategyDecision(
                    StrategyAction.BUY,
                    referencePrice,
                    "10주 이동평균이 40주 이동평균을 상향 돌파했습니다."
            );
        }

        if (crossedBelow) {
            return new StrategyDecision(
                    StrategyAction.SELL,
                    referencePrice,
                    "10주 이동평균이 40주 이동평균을 하향 돌파했습니다."
            );
        }

        return new StrategyDecision(
                StrategyAction.HOLD,
                referencePrice,
                "10주와 40주 이동평균의 교차 조건이 충족되지 않았습니다."
        );
    }
}
