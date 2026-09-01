package com.tradeguide.service.risk;

import com.tradeguide.domain.risk.HoldingExposure;
import com.tradeguide.domain.risk.PortfolioRiskAlert;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.service.portfolio.PortfolioService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioRiskAlertService {

    private final PortfolioService portfolioService;
    private final PortfolioExposureService portfolioExposureService;
    private final PortfolioRiskAlertCalculator portfolioRiskAlertCalculator;

    public PortfolioRiskAlertService(
            PortfolioService portfolioService,
            PortfolioExposureService portfolioExposureService,
            PortfolioRiskAlertCalculator portfolioRiskAlertCalculator
    ) {
        this.portfolioService = portfolioService;
        this.portfolioExposureService = portfolioExposureService;
        this.portfolioRiskAlertCalculator = portfolioRiskAlertCalculator;
    }

    public List<PortfolioRiskAlert> getRiskAlerts(
            Long memberId,
            Long portfolioId
    ) {
        PortfolioRiskPolicy riskPolicy = portfolioService.getRiskPolicy(memberId, portfolioId);

        List<HoldingExposure> exposures = portfolioExposureService.getExposures(memberId, portfolioId);

        return portfolioRiskAlertCalculator.calculate(exposures, riskPolicy);
    }
}
