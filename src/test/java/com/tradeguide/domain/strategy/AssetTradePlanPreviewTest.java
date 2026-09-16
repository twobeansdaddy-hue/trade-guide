package com.tradeguide.domain.strategy;

import com.tradeguide.domain.trade.Market;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetTradePlanPreviewTest {

    private final StrategyMetadata metadata =
            new StrategyMetadata("weekly-ma-crossover", "test-v1", LocalDate.of(2026, 8, 10));

    @Test
    void requiresNotReadyReasonWhenStatusIsNotReady() {
        assertThatThrownBy(() -> new AssetTradePlanPreview(
                Market.US,
                "TQQQ",
                TradePlanPreviewStatus.NOT_READY,
                null,
                new BigDecimal("90"),
                null,
                null,
                null,
                null,
                List.of(),
                "사유",
                metadata
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNotReadyReasonWhenStatusIsNotNotReady() {
        assertThatThrownBy(() -> new AssetTradePlanPreview(
                Market.US,
                "TQQQ",
                TradePlanPreviewStatus.WATCH,
                TradePlanPreviewNotReadyReason.MISSING_STOP_LOSS,
                new BigDecimal("90"),
                null,
                null,
                null,
                null,
                List.of(),
                "사유",
                metadata
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alwaysRequiresUserConfirmation() {
        AssetTradePlanPreview preview = new AssetTradePlanPreview(
                Market.US,
                "TQQQ",
                TradePlanPreviewStatus.BUY,
                null,
                new BigDecimal("90"),
                new BigDecimal("81"),
                new BigDecimal("10"),
                new BigDecimal("900"),
                new BigDecimal("90"),
                List.of(TradePlanPreviewConstraint.AVAILABLE_CASH_NOT_SYNCED),
                "검토용 매수 계획입니다.",
                metadata
        );

        assertThat(preview.isRequiresUserConfirmation()).isTrue();
    }

    @Test
    void defaultsPlannedActionsToEmptyListWhenUsingLegacyConstructor() {
        AssetTradePlanPreview preview = new AssetTradePlanPreview(
                Market.US,
                "TQQQ",
                TradePlanPreviewStatus.WATCH,
                null,
                new BigDecimal("90"),
                null,
                null,
                null,
                null,
                List.of(),
                "사유",
                metadata
        );

        assertThat(preview.getPlannedActions()).isEmpty();
    }

    @Test
    void exposesOrderedPlannedActionsWhenProvided() {
        PlannedTradeAction protectiveExit = new PlannedTradeAction(
                PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW,
                new BigDecimal("81"),
                new BigDecimal("50"),
                new BigDecimal("4050.00"),
                "손절가 도달 시 전량 청산을 검토합니다.",
                metadata
        );
        PlannedTradeAction partialProfit = new PlannedTradeAction(
                PlannedTradeActionType.PARTIAL_PROFIT_REVIEW,
                null,
                null,
                null,
                "검증된 익절 규칙이 아직 등록되지 않았습니다.",
                metadata
        );

        AssetTradePlanPreview preview = new AssetTradePlanPreview(
                Market.US,
                "SOXL",
                TradePlanPreviewStatus.SELL_REVIEW,
                null,
                new BigDecimal("95"),
                new BigDecimal("81"),
                null,
                null,
                null,
                List.of(),
                "사유",
                metadata,
                List.of(protectiveExit, partialProfit)
        );

        assertThat(preview.getPlannedActions())
                .containsExactly(protectiveExit, partialProfit);
    }
}
