package com.tradeguide.domain.strategy;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradePlanTest {

    @Test
    void keepsOrderDraftDetailsSeparateFromStrategyDecision() {
        TradePlan tradePlan = new TradePlan(
                Market.US,
                "SOXL",
                TradeType.BUY,
                new BigDecimal("0.20"),
                new BigDecimal("25.00"),
                new BigDecimal("22.00"),
                LocalDate.of(2026, 8, 17),
                "Track A 주문 초안 테스트",
                new StrategyMetadata(
                        "track-a-weekly-ma-crossover",
                        "v1",
                        LocalDate.of(2026, 8, 10)
                )
        );

        assertThat(tradePlan.getMarket()).isEqualTo(Market.US);
        assertThat(tradePlan.getTicker()).isEqualTo("SOXL");
        assertThat(tradePlan.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(tradePlan.getQuantityRatio())
                .isEqualByComparingTo("0.20");
        assertThat(tradePlan.getLimitPrice())
                .isEqualByComparingTo("25.00");
        assertThat(tradePlan.getStopLossPrice())
                .isEqualByComparingTo("22.00");
        assertThat(tradePlan.getValidUntil())
                .isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(tradePlan.getReason())
                .isEqualTo("Track A 주문 초안 테스트");
        assertThat(tradePlan.getStrategyMetadata().getStrategyId())
                .isEqualTo("track-a-weekly-ma-crossover");
    }

    @Test
    void rejectsQuantityRatioOutsideAllowedRange() {
        assertThatThrownBy(() -> new TradePlan(
                Market.US,
                "SOXL",
                TradeType.BUY,
                BigDecimal.ZERO,
                new BigDecimal("25.00"),
                new BigDecimal("22.00"),
                LocalDate.of(2026, 8, 17),
                "주문 비율 검증",
                new StrategyMetadata(
                        "track-a-weekly-ma-crossover",
                        "v1",
                        LocalDate.of(2026, 8, 10)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 비율은 0보다 크고 1 이하여야 합니다.");

        assertThatThrownBy(() -> new TradePlan(
                Market.US,
                "SOXL",
                TradeType.BUY,
                new BigDecimal("1.01"),
                new BigDecimal("25.00"),
                new BigDecimal("22.00"),
                LocalDate.of(2026, 8, 17),
                "주문 비율 검증",
                new StrategyMetadata(
                        "track-a-weekly-ma-crossover",
                        "v1",
                        LocalDate.of(2026, 8, 10)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 비율은 0보다 크고 1 이하여야 합니다.");
    }

    @Test
    void rejectsNonPositiveLimitPrice() {
        assertThatThrownBy(() -> new TradePlan(
                Market.US,
                "SOXL",
                TradeType.BUY,
                new BigDecimal("0.20"),
                BigDecimal.ZERO,
                new BigDecimal("22.00"),
                LocalDate.of(2026, 8, 17),
                "지정가 검증",
                new StrategyMetadata(
                        "track-a-weekly-ma-crossover",
                        "v1",
                        LocalDate.of(2026, 8, 10)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지정가는 0보다 커야 합니다.");
    }

    @Test
    void rejectsNonPositiveStopLossPrice() {
        assertThatThrownBy(() -> new TradePlan(
                Market.US,
                "SOXL",
                TradeType.BUY,
                new BigDecimal("0.20"),
                new BigDecimal("25.00"),
                BigDecimal.ZERO,
                LocalDate.of(2026, 8, 17),
                "손절가 검증",
                new StrategyMetadata(
                        "track-a-weekly-ma-crossover",
                        "v1",
                        LocalDate.of(2026, 8, 10)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("손절가는 0보다 커야 합니다.");
    }

    @Test
    void rejectsBlankTicker() {
        assertThatThrownBy(() -> new TradePlan(
                Market.US,
                " ",
                TradeType.BUY,
                new BigDecimal("0.20"),
                new BigDecimal("25.00"),
                new BigDecimal("22.00"),
                LocalDate.of(2026, 8, 17),
                "티커 검증",
                new StrategyMetadata(
                        "track-a-weekly-ma-crossover",
                        "v1",
                        LocalDate.of(2026, 8, 10)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("티커는 비어 있을 수 없습니다.");
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThatThrownBy(() -> new TradePlan(
                null, "SOXL", TradeType.BUY,
                new BigDecimal("0.20"), new BigDecimal("25.00"),
                new BigDecimal("22.00"), LocalDate.of(2026, 8, 17),
                "필수값 검증", validMetadata()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시장은 필수입니다.");

        assertThatThrownBy(() -> new TradePlan(
                Market.US, "SOXL", null,
                new BigDecimal("0.20"), new BigDecimal("25.00"),
                new BigDecimal("22.00"), LocalDate.of(2026, 8, 17),
                "필수값 검증", validMetadata()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 유형은 필수입니다.");

        assertThatThrownBy(() -> new TradePlan(
                Market.US, "SOXL", TradeType.BUY,
                new BigDecimal("0.20"), new BigDecimal("25.00"),
                new BigDecimal("22.00"), null,
                "필수값 검증", validMetadata()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 유효 기간은 필수입니다.");

        assertThatThrownBy(() -> new TradePlan(
                Market.US, "SOXL", TradeType.BUY,
                new BigDecimal("0.20"), new BigDecimal("25.00"),
                new BigDecimal("22.00"), LocalDate.of(2026, 8, 17),
                "필수값 검증", null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("전략 메타데이터는 필수입니다.");
    }

    @Test
    void rejectsBlankReason() {
        assertThatThrownBy(() -> new TradePlan(
                Market.US,
                "SOXL",
                TradeType.BUY,
                new BigDecimal("0.20"),
                new BigDecimal("25.00"),
                new BigDecimal("22.00"),
                LocalDate.of(2026, 8, 17),
                " ",
                validMetadata()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 근거는 비어 있을 수 없습니다.");
    }

    private StrategyMetadata validMetadata() {
        return new StrategyMetadata(
                "track-a-weekly-ma-crossover",
                "v1",
                LocalDate.of(2026, 8, 10)
        );
    }
}