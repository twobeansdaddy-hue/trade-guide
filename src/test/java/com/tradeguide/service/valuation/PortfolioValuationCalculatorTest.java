package com.tradeguide.service.valuation;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioValuationCalculatorTest {

    private final PortfolioValuationCalculator calculator =
            new PortfolioValuationCalculator();

    @Test
    void calculatesPortfolioValuation() {
        // given
        HoldingValuation aapl = new HoldingValuation(
                Market.US,
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("1000"),
                new BigDecimal("1100"),
                new BigDecimal("100"),
                new BigDecimal("10")
        );

        HoldingValuation msft = new HoldingValuation(
                Market.US,
                "MSFT",
                new BigDecimal("10"),
                new BigDecimal("200"),
                new BigDecimal("180"),
                new BigDecimal("2000"),
                new BigDecimal("1800"),
                new BigDecimal("-200"),
                new BigDecimal("-10")
        );

        // when
        PortfolioValuation result = calculator.calculate(List.of(aapl, msft));

        // then
        assertThat(result.getHoldingValuations()).hasSize(2);
        assertThat(result.getTotalPurchaseAmount())
                .isEqualByComparingTo("3000");
        assertThat(result.getTotalMarketValue())
                .isEqualByComparingTo("2900");
        assertThat(result.getTotalUnrealizedProfitLoss())
                .isEqualByComparingTo("-100");
        assertThat(result.getTotalReturnRate())
                .isEqualByComparingTo("-3.3333333333");
    }

    @Test
    void returnsZeroWhenThereAreNoHoldings() {
        // when
        PortfolioValuation result = calculator.calculate(List.of());

        // then
        assertThat(result.getHoldingValuations()).isEmpty();
        assertThat(result.getTotalPurchaseAmount())
                .isEqualByComparingTo("0");
        assertThat(result.getTotalMarketValue())
                .isEqualByComparingTo("0");
        assertThat(result.getTotalUnrealizedProfitLoss())
                .isEqualByComparingTo("0");
        assertThat(result.getTotalReturnRate())
                .isEqualByComparingTo("0");
    }
}