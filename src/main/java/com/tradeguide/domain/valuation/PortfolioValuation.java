package com.tradeguide.domain.valuation;

import java.math.BigDecimal;
import java.util.List;

public class PortfolioValuation {
    private final List<HoldingValuation> holdingValuations;
    private final BigDecimal totalPurchaseAmount;
    private final BigDecimal totalMarketValue;
    private final BigDecimal totalUnrealizedProfitLoss;
    private final BigDecimal totalReturnRate;

    public PortfolioValuation(
            List<HoldingValuation> holdingValuations,
            BigDecimal totalPurchaseAmount,
            BigDecimal totalMarketValue,
            BigDecimal totalUnrealizedProfitLoss,
            BigDecimal totalReturnRate)
    {
        this.holdingValuations = holdingValuations;
        this.totalPurchaseAmount = totalPurchaseAmount;
        this.totalMarketValue = totalMarketValue;
        this.totalUnrealizedProfitLoss = totalUnrealizedProfitLoss;
        this.totalReturnRate = totalReturnRate;
    }

    public List<HoldingValuation> getHoldingValuations() {
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
