package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.StrategySignalEvent;
import com.tradeguide.domain.strategy.StrategyTrend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyDecisionMakerTest {

    private final StrategyDecisionMaker strategyDecisionMaker =
            new StrategyDecisionMaker();

    @Test
    void returnsHoldForHeldAssetInAboveTrend() {
        StrategySignal signal = signal(
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                4
        );

        StrategyDecision decision =
                strategyDecisionMaker.decideForHolding(signal);

        assertThat(decision.getAction()).isEqualTo(StrategyAction.HOLD);
        assertThat(decision.getSignal()).isSameAs(signal);
    }

    @Test
    void returnsSellForHeldAssetInBelowTrend() {
        StrategySignal signal = signal(
                StrategyTrend.BELOW_LONG_AVERAGE,
                StrategySignalEvent.CROSS_DOWN,
                0
        );

        StrategyDecision decision =
                strategyDecisionMaker.decideForHolding(signal);

        assertThat(decision.getAction()).isEqualTo(StrategyAction.SELL);
        assertThat(decision.getSignal()).isSameAs(signal);
    }

    @Test
    void returnsBuyForCandidateWithinFourWeeksAfterCross() {
        StrategySignal signal = signal(
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                4
        );

        StrategyDecision decision =
                strategyDecisionMaker.decideForCandidate(signal);

        assertThat(decision.getAction()).isEqualTo(StrategyAction.BUY);
        assertThat(decision.getReason()).isEqualTo(
                "상승 추세가 유지되고 있고 최근 교차 후 4주 이내여서 신규 진입을 검토합니다."
        );
        assertThat(decision.getSignal()).isSameAs(signal);
    }

    @Test
    void returnsWatchForCandidateMoreThanFourWeeksAfterCross() {
        StrategySignal signal = signal(
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                5
        );

        StrategyDecision decision =
                strategyDecisionMaker.decideForCandidate(signal);

        assertThat(decision.getAction()).isEqualTo(StrategyAction.WATCH);
        assertThat(decision.getReason()).isEqualTo("신규 진입 조건이 충족되지 않아 관찰합니다.");
        assertThat(decision.getSignal()).isSameAs(signal);
    }

    @Test
    void returnsWatchForCandidateInBelowTrend() {
        StrategySignal signal = signal(
                StrategyTrend.BELOW_LONG_AVERAGE,
                StrategySignalEvent.CROSS_DOWN,
                0
        );

        StrategyDecision decision =
                strategyDecisionMaker.decideForCandidate(signal);

        assertThat(decision.getAction()).isEqualTo(StrategyAction.WATCH);
        assertThat(decision.getSignal()).isSameAs(signal);
    }

    @Test
    void returnsWatchForCandidateWhenCrossHistoryIsUnknown() {
        StrategySignal signal = signal(
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                null
        );

        StrategyDecision decision =
                strategyDecisionMaker.decideForCandidate(signal);

        assertThat(decision.getAction()).isEqualTo(StrategyAction.WATCH);
        assertThat(decision.getReason())
                .isEqualTo("신규 진입 조건이 충족되지 않아 관찰합니다.");
        assertThat(decision.getSignal()).isSameAs(signal);
    }

    private StrategySignal signal(
            StrategyTrend trend,
            StrategySignalEvent signalEvent,
            Integer weeksSinceCross
    ) {
        return new StrategySignal(
                new BigDecimal("120.25"),
                "테스트 시장 신호",
                new StrategyMetadata(
                        "track-a-weekly-ma-crossover",
                        "v1",
                        LocalDate.of(2026, 8, 3)
                ),
                trend,
                signalEvent,
                weeksSinceCross
        );
    }
}
