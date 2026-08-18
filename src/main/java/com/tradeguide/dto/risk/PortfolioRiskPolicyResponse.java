package com.tradeguide.dto.risk;

import com.tradeguide.domain.risk.PortfolioRiskPolicy;

import java.math.BigDecimal;

public class PortfolioRiskPolicyResponse {

    private final BigDecimal maxLossPerTradeRatio;
    private final BigDecimal maxSingleAssetExposureRatio;

    private PortfolioRiskPolicyResponse(
            BigDecimal maxLossPerTradeRatio,
            BigDecimal maxSingleAssetExposureRatio
    ) {
        this.maxLossPerTradeRatio = maxLossPerTradeRatio;
        this.maxSingleAssetExposureRatio = maxSingleAssetExposureRatio;
    }

    public static PortfolioRiskPolicyResponse from(PortfolioRiskPolicy riskPolicy) {
        return new PortfolioRiskPolicyResponse(
                riskPolicy.getMaxLossPerTradeRatio(),
                riskPolicy.getMaxSingleAssetExposureRatio()
        );
    }

    public BigDecimal getMaxLossPerTradeRatio() {
        return maxLossPerTradeRatio;
    }

    public BigDecimal getMaxSingleAssetExposureRatio() {
        return maxSingleAssetExposureRatio;
    }
}
