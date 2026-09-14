package com.tradeguide.domain.risk;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class PortfolioRiskPolicy {

    private BigDecimal maxLossPerTradeRatio;
    private BigDecimal maxSingleAssetExposureRatio;
    private BigDecimal stopLossRatio;

    protected PortfolioRiskPolicy() {

    }

    public PortfolioRiskPolicy(
            BigDecimal maxLossPerTradeRatio,
            BigDecimal maxSingleAssetExposureRatio
    ) {
        this(maxLossPerTradeRatio, maxSingleAssetExposureRatio, null);
    }

    public PortfolioRiskPolicy(
            BigDecimal maxLossPerTradeRatio,
            BigDecimal maxSingleAssetExposureRatio,
            BigDecimal stopLossRatio
    ) {
        if (maxLossPerTradeRatio == null
                || maxLossPerTradeRatio.compareTo(BigDecimal.ZERO) <= 0
                || maxLossPerTradeRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("주문당 최대 손실 비율은 0보다 크고 1 이하여야 합니다.");
        }

        if (maxSingleAssetExposureRatio == null
                || maxSingleAssetExposureRatio.compareTo(BigDecimal.ZERO) <= 0
                || maxSingleAssetExposureRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("종목당 최대 노출 비율은 0보다 크고 1 이하여야 합니다.");
        }

        if (maxLossPerTradeRatio.compareTo(maxSingleAssetExposureRatio) > 0) {
            throw new IllegalArgumentException("주문당 최대 손실 비율은 종목당 최대 노출 비율을 초과할 수 없습니다.");
        }

        if (stopLossRatio != null
                && (stopLossRatio.compareTo(BigDecimal.ZERO) <= 0
                || stopLossRatio.compareTo(BigDecimal.ONE) >= 0)) {
            throw new IllegalArgumentException("손절 기준 비율은 0보다 크고 1보다 작아야 합니다.");
        }

        this.maxLossPerTradeRatio = maxLossPerTradeRatio;
        this.maxSingleAssetExposureRatio = maxSingleAssetExposureRatio;
        this.stopLossRatio = stopLossRatio;
    }

    public BigDecimal getMaxLossPerTradeRatio() {
        return maxLossPerTradeRatio;
    }

    public BigDecimal getMaxSingleAssetExposureRatio() {
        return maxSingleAssetExposureRatio;
    }

    public BigDecimal getStopLossRatio() {
        return stopLossRatio;
    }
}
