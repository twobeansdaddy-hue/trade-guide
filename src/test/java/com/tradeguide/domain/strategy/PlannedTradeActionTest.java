package com.tradeguide.domain.strategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlannedTradeActionTest {

    private final StrategyMetadata metadata =
            new StrategyMetadata("weekly-ma-crossover", "test-v1", LocalDate.of(2026, 8, 10));

    @Test
    void requiresType() {
        assertThatThrownBy(() -> new PlannedTradeAction(
                null,
                new BigDecimal("81"),
                new BigDecimal("50"),
                new BigDecimal("4050"),
                "사유",
                metadata
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresReason() {
        assertThatThrownBy(() -> new PlannedTradeAction(
                PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW,
                new BigDecimal("81"),
                new BigDecimal("50"),
                new BigDecimal("4050"),
                " ",
                metadata
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresStrategyMetadata() {
        assertThatThrownBy(() -> new PlannedTradeAction(
                PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW,
                new BigDecimal("81"),
                new BigDecimal("50"),
                new BigDecimal("4050"),
                "사유",
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsNullTriggerPriceAndQuantityForNonActionableEntries() {
        PlannedTradeAction action = new PlannedTradeAction(
                PlannedTradeActionType.PARTIAL_PROFIT_REVIEW,
                null,
                null,
                null,
                "검증된 익절 규칙이 아직 등록되지 않았습니다.",
                metadata
        );

        assertThat(action.getTriggerPrice()).isNull();
        assertThat(action.getQuantity()).isNull();
        assertThat(action.getAmount()).isNull();
    }

    @Test
    void alwaysRequiresUserConfirmation() {
        PlannedTradeAction action = new PlannedTradeAction(
                PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW,
                new BigDecimal("81"),
                new BigDecimal("50"),
                new BigDecimal("4050"),
                "사유",
                metadata
        );

        assertThat(action.isRequiresUserConfirmation()).isTrue();
    }
}
