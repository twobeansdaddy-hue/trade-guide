package com.tradeguide.service.risk;

import com.tradeguide.domain.risk.HoldingExposure;
import com.tradeguide.domain.risk.PortfolioRiskAlert;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioRiskAlertCalculatorTest {

    private final PortfolioRiskAlertCalculator calculator =
            new PortfolioRiskAlertCalculator();

    @Test
    void returnsAlertsOnlyForHoldingsExceedingMaximumExposure() {
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.025"),
                new BigDecimal("0.125")
        );

        List<HoldingExposure> exposures = List.of(
                exposure("SOXL", "600", "30.00"),
                exposure("AAPL", "250", "12.50"),
                exposure("MSFT", "150", "7.50")
        );

        List<PortfolioRiskAlert> alerts = calculator.calculate(
                exposures,
                riskPolicy
        );

        assertThat(alerts).hasSize(1);

        PortfolioRiskAlert alert = alerts.get(0);
        assertThat(alert.getMarket()).isEqualTo(Market.US);
        assertThat(alert.getTicker()).isEqualTo("SOXL");
        assertThat(alert.getExposureRate())
                .isEqualByComparingTo("30.00");
        assertThat(alert.getMaxExposureRate())
                .isEqualByComparingTo("12.50");
        assertThat(alert.getMessage())
                .isEqualTo("종목별 최대 노출 비율을 초과했습니다.");
    }

    private HoldingExposure exposure(
            String ticker,
            String marketValue,
            String exposureRate
    ) {
        return new HoldingExposure(
                Market.US,
                ticker,
                new BigDecimal(marketValue),
                new BigDecimal(exposureRate)
        );
    }
}