package com.tradeguide.service.risk;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioExposureCalculatorTest {

    private final PortfolioExposureCalculator calculator =
            new PortfolioExposureCalculator();

    @Test
    void calculatesHoldingExposureRates() {
        PortfolioValuation portfolioValuation = new PortfolioValuation(
                List.of(
                        holdingValuation("SOXL", "600"),
                        holdingValuation("AAPL", "1400")
                ),
                new BigDecimal("2000"),
                new BigDecimal("2000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        var result = calculator.calculate(portfolioValuation);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getExposureRate())
                .isEqualByComparingTo("30.00");
        assertThat(result.get(1).getExposureRate())
                .isEqualByComparingTo("70.00");
    }

    @Test
    void returnsEmptyListWhenTotalMarketValueIsZero() {
        PortfolioValuation portfolioValuation = new PortfolioValuation(
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        assertThat(calculator.calculate(portfolioValuation)).isEmpty();
    }

    private HoldingValuation holdingValuation(
            String ticker,
            String marketValue
    ) {
        BigDecimal value = new BigDecimal(marketValue);

        return new HoldingValuation(
                Market.US,
                ticker,
                BigDecimal.ONE,
                value,
                value,
                value,
                value,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}