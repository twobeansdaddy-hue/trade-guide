package com.tradeguide.service.risk;

import com.tradeguide.domain.risk.HoldingExposure;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.service.valuation.PortfolioValuationService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioExposureService {

    private final PortfolioValuationService portfolioValuationService;
    private final PortfolioExposureCalculator portfolioExposureCalculator;

    public PortfolioExposureService(
            PortfolioValuationService portfolioValuationService,
            PortfolioExposureCalculator portfolioExposureCalculator) {
        this.portfolioValuationService = portfolioValuationService;
        this.portfolioExposureCalculator = portfolioExposureCalculator;
    }

    public List<HoldingExposure> getExposures(
            Long memberId,
            Long portfolioId
    ) {
        PortfolioValuation portfolioValuation =
                portfolioValuationService.getPortfolioValuation(
                        memberId,
                        portfolioId
                );

        return portfolioExposureCalculator.calculate(portfolioValuation);
    }
}
