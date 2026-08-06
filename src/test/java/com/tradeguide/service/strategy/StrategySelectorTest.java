package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.service.indicator.SimpleMovingAverageCalculator;
import com.tradeguide.service.strategy.tracka.WeeklyMaCrossoverStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StrategySelectorTest {

    private final TradingStrategy trackAStrategy =
            new WeeklyMaCrossoverStrategy(new SimpleMovingAverageCalculator());

    private final StrategySelector strategySelector =
            new StrategySelector(List.of(trackAStrategy));

    @Test
    void selectsStrategyForTrackA() {
        TradingStrategy result =
                strategySelector.select(InvestmentTrack.TRACK_A);

        assertThat(result).isSameAs(trackAStrategy);
    }

    @Test
    void throwsExceptionWhenNoStrategySupportsTrack() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        strategySelector.select(InvestmentTrack.TRACK_B))
                .withMessage(
                        "지원하지 않는 투자 트랙입니다: TRACK_B"
                );
    }
}