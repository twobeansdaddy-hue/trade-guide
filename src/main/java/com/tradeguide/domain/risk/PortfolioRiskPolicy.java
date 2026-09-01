package com.tradeguide.domain.risk;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class PortfolioRiskPolicy {

    private BigDecimal maxLossPerTradeRatio;
    private BigDecimal maxSingleAssetExposureRatio;

    protected PortfolioRiskPolicy() {

    }

    public PortfolioRiskPolicy(
            BigDecimal maxLossPerTradeRatio,
            BigDecimal maxSingleAssetExposureRatio
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

        this.maxLossPerTradeRatio = maxLossPerTradeRatio;
        this.maxSingleAssetExposureRatio = maxSingleAssetExposureRatio;
    }

    public BigDecimal getMaxLossPerTradeRatio() {
        return maxLossPerTradeRatio;
    }

    public BigDecimal getMaxSingleAssetExposureRatio() {
        return maxSingleAssetExposureRatio;
    }
}
