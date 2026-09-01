package com.tradeguide.domain.risk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioRiskPolicyTest {

    @Test
    void keepsPortfolioRiskLimits() {
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("0.20")
        );

        assertThat(riskPolicy.getMaxLossPerTradeRatio())
                .isEqualByComparingTo("0.02");
        assertThat(riskPolicy.getMaxSingleAssetExposureRatio())
                .isEqualByComparingTo("0.20");
    }

    @Test
    void rejectsRiskRatioOutsideAllowedRange() {
        assertThatThrownBy(() -> new PortfolioRiskPolicy(
                BigDecimal.ZERO,
                new BigDecimal("0.20")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문당 최대 손실 비율은 0보다 크고 1 이하여야 합니다.");
    }

    @Test
    void rejectsExposureRatioOutsideAllowedRange() {
        assertThatThrownBy(() -> new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("1.01")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("종목당 최대 노출 비율은 0보다 크고 1 이하여야 합니다.");
    }

    @Test
    void rejectsLossRatioGreaterThanMaximumExposureRatio() {
        assertThatThrownBy(() -> new PortfolioRiskPolicy(
                new BigDecimal("0.30"),
                new BigDecimal("0.20")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "주문당 최대 손실 비율은 종목당 최대 노출 비율을 초과할 수 없습니다."
                );
    }
}