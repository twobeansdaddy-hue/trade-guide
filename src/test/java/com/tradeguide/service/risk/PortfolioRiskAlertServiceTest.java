package com.tradeguide.service.risk;

import com.tradeguide.domain.risk.HoldingExposure;
import com.tradeguide.domain.risk.PortfolioRiskAlert;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.service.portfolio.PortfolioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioRiskAlertServiceTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private PortfolioExposureService portfolioExposureService;

    @Mock
    private PortfolioRiskAlertCalculator portfolioRiskAlertCalculator;

    @InjectMocks
    private PortfolioRiskAlertService portfolioRiskAlertService;

    @Test
    void getsRiskAlertsForPortfolio() {
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.025"),
                new BigDecimal("0.125")
        );
        List<HoldingExposure> exposures = List.of(
                new HoldingExposure(
                        Market.US,
                        "SOXL",
                        new BigDecimal("600"),
                        new BigDecimal("30.00")
                )
        );
        List<PortfolioRiskAlert> expected = List.of(
                new PortfolioRiskAlert(
                        Market.US,
                        "SOXL",
                        new BigDecimal("30.00"),
                        new BigDecimal("12.50"),
                        "종목별 최대 노출 비율을 초과했습니다."
                )
        );

        when(portfolioService.getRiskPolicy(10L, 100L))
                .thenReturn(riskPolicy);
        when(portfolioExposureService.getExposures(10L, 100L))
                .thenReturn(exposures);
        when(portfolioRiskAlertCalculator.calculate(
                exposures,
                riskPolicy
        )).thenReturn(expected);

        List<PortfolioRiskAlert> result =
                portfolioRiskAlertService.getRiskAlerts(10L, 100L);

        assertThat(result).isSameAs(expected);
        verify(portfolioService).getRiskPolicy(10L, 100L);
        verify(portfolioExposureService).getExposures(10L, 100L);
        verify(portfolioRiskAlertCalculator).calculate(exposures, riskPolicy);
    }
}