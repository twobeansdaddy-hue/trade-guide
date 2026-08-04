package com.tradeguide.dto.valuation;

import com.tradeguide.domain.valuation.PortfolioValuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class PortfolioValuationResponse {
    private final List<HoldingValuationResponse> holdingValuations;
    private final BigDecimal totalPurchaseAmount;
    private final BigDecimal totalMarketValue;
    private final BigDecimal totalUnrealizedProfitLoss;
    private final BigDecimal totalReturnRate;

    public PortfolioValuationResponse(
            List<HoldingValuationResponse> holdingValuations,
            BigDecimal totalPurchaseAmount,
            BigDecimal totalMarketValue,
            BigDecimal totalUnrealizedProfitLoss,
            BigDecimal totalReturnRate
    ) {
        this.holdingValuations = holdingValuations;
        this.totalPurchaseAmount = totalPurchaseAmount;
        this.totalMarketValue = totalMarketValue;
        this.totalUnrealizedProfitLoss = totalUnrealizedProfitLoss;
        this.totalReturnRate = totalReturnRate;
    }

    public static PortfolioValuationResponse from(PortfolioValuation valuation) {
        return new PortfolioValuationResponse(
                valuation.getHoldingValuations().stream()
                        .map(HoldingValuationResponse::from)
                        .toList(),
                valuation.getTotalPurchaseAmount().setScale(2, RoundingMode.HALF_UP),
                valuation.getTotalMarketValue().setScale(2, RoundingMode.HALF_UP),
                valuation.getTotalUnrealizedProfitLoss().setScale(2, RoundingMode.HALF_UP),
                valuation.getTotalReturnRate().setScale(2, RoundingMode.HALF_UP)
        );
    }

    public List<HoldingValuationResponse> getHoldingValuations() {
        return holdingValuations;
    }

    public BigDecimal getTotalPurchaseAmount() {
        return totalPurchaseAmount;
    }

    public BigDecimal getTotalMarketValue() {
        return totalMarketValue;
    }

    public BigDecimal getTotalUnrealizedProfitLoss() {
        return totalUnrealizedProfitLoss;
    }

    public BigDecimal getTotalReturnRate() {
        return totalReturnRate;
    }
}
