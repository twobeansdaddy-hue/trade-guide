package com.tradeguide.domain.strategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyDecisionTest {

    @Test
    void keepsActionReasonAndMarketSignalSeparately() {
        StrategySignal signal = new StrategySignal(
                new BigDecimal("140.25"),
                "10주 이동평균이 40주 이동평균 위에 있습니다.",
                new StrategyMetadata(
                        "track-a-weekly-ma-crossover",
                        "v1",
                        LocalDate.of(2026, 8, 3)
                ),
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                4
        );

        StrategyDecision decision = new StrategyDecision(
                StrategyAction.HOLD,
                "상승 추세가 유지되고 있어 현재 보유 수량을 유지합니다.",
                signal
        );

        assertThat(decision.getAction()).isEqualTo(StrategyAction.HOLD);
        assertThat(decision.getReason())
                .isEqualTo("상승 추세가 유지되고 있어 현재 보유 수량을 유지합니다.");
        assertThat(decision.getSignal()).isSameAs(signal);
    }
}