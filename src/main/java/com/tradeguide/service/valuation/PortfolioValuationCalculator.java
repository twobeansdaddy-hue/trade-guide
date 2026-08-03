package com.tradeguide.service.valuation;

import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PortfolioValuationCalculator {

    public PortfolioValuation calculate(List<HoldingValuation> holdingValuations) {

        BigDecimal totalPurchaseAmount = holdingValuations.stream()
                .map(HoldingValuation::getPurchaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMarketValue = holdingValuations.stream()
                .map(HoldingValuation::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnrealizedProfitLoss = holdingValuations.stream()
                .map(HoldingValuation::getUnrealizedProfitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReturnRate;

        if (totalPurchaseAmount.compareTo(BigDecimal.ZERO) == 0) {
            totalReturnRate = BigDecimal.ZERO;
        } else {
            totalReturnRate = totalUnrealizedProfitLoss
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalPurchaseAmount, 10, RoundingMode.HALF_UP);

        }

        return new PortfolioValuation(
                List.copyOf(holdingValuations),
                totalPurchaseAmount,
                totalMarketValue,
                totalUnrealizedProfitLoss,
                totalReturnRate
        );
    }
}
