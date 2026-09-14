package com.tradeguide.service.backtest;

import com.tradeguide.domain.backtest.MomentumFormationScore;
import com.tradeguide.domain.backtest.MomentumTierSelectionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MomentumTopCandidateSelectorTest {

    private final MomentumTopCandidateSelector selector = new MomentumTopCandidateSelector();
    private final LocalDate rebalanceDate = LocalDate.of(2026, 3, 27);

    @Test
    void firstRebalanceEntersOnlyTopTierTickers() {
        // Given: 5종목 순위, 상위 20%(1종목) 신규 진입, 하위 60%(3종목)까지 유지 허용, 기존 보유 없음
        List<MomentumFormationScore> rankedScores = rankedScores("A", "B", "C", "D", "E");

        // When
        MomentumTierSelectionResult result = selector.select(
                rankedScores, Set.of(), new BigDecimal("0.2"), new BigDecimal("0.6")
        );

        // Then
        assertThat(result.getNewHoldings()).containsExactlyInAnyOrder("A");
        assertThat(result.getEnteredTickers()).containsExactly("A");
        assertThat(result.getRetainedTickers()).isEmpty();
        assertThat(result.getExitedTickers()).isEmpty();
    }

    @Test
    void retainsHeldTickerWithinHoldTierEvenWhenOutsideTopTier() {
        // Given: C는 3위(상위 20% 밖이지만 하위 60% 안)에 이미 보유 중
        List<MomentumFormationScore> rankedScores = rankedScores("A", "B", "C", "D", "E");
        Set<String> previousHoldings = new LinkedHashSet<>(Set.of("C"));

        // When
        MomentumTierSelectionResult result = selector.select(
                rankedScores, previousHoldings, new BigDecimal("0.2"), new BigDecimal("0.6")
        );

        // Then: A(신규 진입) + C(유지), D/E는 애초에 보유하지 않았으므로 이탈 목록에 없음
        assertThat(result.getNewHoldings()).containsExactlyInAnyOrder("A", "C");
        assertThat(result.getEnteredTickers()).containsExactly("A");
        assertThat(result.getRetainedTickers()).containsExactly("C");
        assertThat(result.getExitedTickers()).isEmpty();
    }

    @Test
    void exitsHeldTickerThatFallsOutsideHoldTier() {
        // Given: D는 4위(하위 60% 밖)인데 이미 보유 중
        List<MomentumFormationScore> rankedScores = rankedScores("A", "B", "C", "D", "E");
        Set<String> previousHoldings = new LinkedHashSet<>(Set.of("D"));

        // When
        MomentumTierSelectionResult result = selector.select(
                rankedScores, previousHoldings, new BigDecimal("0.2"), new BigDecimal("0.6")
        );

        // Then
        assertThat(result.getNewHoldings()).containsExactlyInAnyOrder("A");
        assertThat(result.getExitedTickers()).containsExactly("D");
    }

    @Test
    void throwsWhenTopTierFractionExceedsHoldTierFraction() {
        List<MomentumFormationScore> rankedScores = rankedScores("A", "B", "C");

        assertThatIllegalArgumentException().isThrownBy(() -> selector.select(
                rankedScores, Set.of(), new BigDecimal("0.6"), new BigDecimal("0.2")
        ));
    }

    @Test
    void throwsWhenFractionIsOutOfRange() {
        List<MomentumFormationScore> rankedScores = rankedScores("A", "B", "C");

        assertThatIllegalArgumentException().isThrownBy(() -> selector.select(
                rankedScores, Set.of(), BigDecimal.ZERO, new BigDecimal("0.5")
        ));

        assertThatIllegalArgumentException().isThrownBy(() -> selector.select(
                rankedScores, Set.of(), new BigDecimal("0.2"), new BigDecimal("1.5")
        ));
    }

    private List<MomentumFormationScore> rankedScores(String... tickersInRankOrder) {
        return java.util.stream.IntStream.range(0, tickersInRankOrder.length)
                .mapToObj(i -> new MomentumFormationScore(
                        tickersInRankOrder[i],
                        rebalanceDate,
                        BigDecimal.valueOf(100 - i),
                        i + 1
                ))
                .toList();
    }
}
