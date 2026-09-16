package com.tradeguide.service.valuation;

import com.tradeguide.domain.trade.Currency;
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
        var usdTotals = result.getTotalsFor(Currency.USD);
        assertThat(usdTotals.getTotalPurchaseAmount())
                .isEqualByComparingTo("3000");
        assertThat(usdTotals.getTotalMarketValue())
                .isEqualByComparingTo("2900");
        assertThat(usdTotals.getTotalUnrealizedProfitLoss())
                .isEqualByComparingTo("-100");
        assertThat(usdTotals.getTotalReturnRate())
                .isEqualByComparingTo("-3.3333333333");
    }

    @Test
    void returnsZeroWhenThereAreNoHoldings() {
        // when
        PortfolioValuation result = calculator.calculate(List.of());

        // then
        assertThat(result.getHoldingValuations()).isEmpty();
        assertThat(result.getTotalsByCurrency()).isEmpty();
        var usdTotals = result.getTotalsFor(Currency.USD);
        assertThat(usdTotals.getTotalPurchaseAmount()).isEqualByComparingTo("0");
        assertThat(usdTotals.getTotalMarketValue()).isEqualByComparingTo("0");
        assertThat(usdTotals.getTotalUnrealizedProfitLoss()).isEqualByComparingTo("0");
        assertThat(usdTotals.getTotalReturnRate()).isEqualByComparingTo("0");
    }

    /**
     * USD와 KRW 보유 종목이 섞여 있으면 환율 변환 없이 통화별로 각각 합계를 낸다 -
     * 서로 다른 통화의 값을 하나로 합치지 않는다.
     */
    @Test
    void keepsUsdAndKrwTotalsSeparateWithoutConvertingBetweenThem() {
        HoldingValuation aapl = new HoldingValuation(
                Market.US, "AAPL",
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("110"),
                new BigDecimal("1000"), new BigDecimal("1100"), new BigDecimal("100"), new BigDecimal("10")
        );
        HoldingValuation samsung = new HoldingValuation(
                Market.KR, "005930",
                new BigDecimal("5"), new BigDecimal("70000"), new BigDecimal("75000"),
                new BigDecimal("350000"), new BigDecimal("375000"), new BigDecimal("25000"), new BigDecimal("7.14")
        );

        PortfolioValuation result = calculator.calculate(List.of(aapl, samsung));

        assertThat(result.getTotalsByCurrency()).containsOnlyKeys(Currency.USD, Currency.KRW);
        assertThat(result.getTotalsFor(Currency.USD).getTotalMarketValue()).isEqualByComparingTo("1100");
        assertThat(result.getTotalsFor(Currency.KRW).getTotalMarketValue()).isEqualByComparingTo("375000");
    }
}
