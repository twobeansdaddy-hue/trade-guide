package com.tradeguide.service.risk;

import com.tradeguide.domain.risk.HoldingExposure;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PortfolioExposureCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public List<HoldingExposure> calculate(
            PortfolioValuation portfolioValuation
    ) {
        BigDecimal totalMarketValue = portfolioValuation.getTotalMarketValue();

        if (totalMarketValue.signum() == 0) {
            return List.of();
        }

        return portfolioValuation.getHoldingValuations().stream()
                .map(holdingValuation -> createExposure(
                        holdingValuation,
                        totalMarketValue
                ))
                .toList();
    }

    private HoldingExposure createExposure(
            HoldingValuation holdingValuation,
            BigDecimal totalMarketValue
    ) {
        BigDecimal exposureRate = holdingValuation.getMarketValue()
                .multiply(ONE_HUNDRED)
                .divide(totalMarketValue, 2, RoundingMode.HALF_UP);

        return new HoldingExposure(
                holdingValuation.getMarket(),
                holdingValuation.getTicker(),
                holdingValuation.getMarketValue(),
                exposureRate
        );
    }
}
