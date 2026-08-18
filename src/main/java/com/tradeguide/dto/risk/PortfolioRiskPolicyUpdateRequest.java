package com.tradeguide.dto.risk;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class PortfolioRiskPolicyUpdateRequest {

    @NotNull(message = "주문당 최대 손실 비율은 필수입니다.")
    @DecimalMin(
            value = "0.0",
            inclusive = false,
            message = "주문당 최대 손실 비율은 0보다 커야 합니다."
    )
    @DecimalMax(
            value = "1.0",
            message = "주문당 최대 손실 비율은 1 이하여야 합니다."
    )
    private BigDecimal maxLossPerTradeRatio;

    @NotNull(message = "종목별 최대 손실 비율은 필수입니다.")
    @DecimalMin(
            value = "0.0",
            inclusive = false,
            message = "종목별당 최대 손실 비율은 0보다 커야 합니다."
    )
    @DecimalMax(
            value = "1.0",
            message = "종목별 최대 손실 비율은 1 이하여야 합니다."
    )
    private BigDecimal maxSingleAssetExposureRatio;

    public BigDecimal getMaxLossPerTradeRatio() {
        return maxLossPerTradeRatio;
    }

    public BigDecimal getMaxSingleAssetExposureRatio() {
        return maxSingleAssetExposureRatio;
    }
}
