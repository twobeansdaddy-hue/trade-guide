package com.tradeguide.service.risk;

import com.tradeguide.domain.trade.Currency;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.CurrencyValuationTotals;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioExposureCalculatorTest {

    private final PortfolioExposureCalculator calculator =
            new PortfolioExposureCalculator();

    @Test
    void calculatesHoldingExposureRates() {
        PortfolioValuation portfolioValuation = new PortfolioValuation(
                List.of(
                        holdingValuation(Market.US, "SOXL", "600"),
                        holdingValuation(Market.US, "AAPL", "1400")
                ),
                Map.of(Currency.USD, usdTotals("2000", "2000"))
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
                Map.of()
        );

        assertThat(calculator.calculate(portfolioValuation)).isEmpty();
    }

    /**
     * KRW 보유 종목은 환율 변환 정책이 없어 USD 노출 비중 계산에서 제외한다 - USD
     * 총액에도, 결과 목록에도 나타나지 않아야 한다.
     */
    @Test
    void excludesKrwHoldingsFromUsExposureCalculation() {
        PortfolioValuation portfolioValuation = new PortfolioValuation(
                List.of(
                        holdingValuation(Market.US, "AAPL", "1000"),
                        holdingValuation(Market.KR, "005930", "5000000")
                ),
                Map.of(
                        Currency.USD, usdTotals("1000", "1000"),
                        Currency.KRW, new CurrencyValuationTotals(
                                Currency.KRW, new BigDecimal("5000000"), new BigDecimal("5000000"),
                                BigDecimal.ZERO, BigDecimal.ZERO)
                )
        );

        var result = calculator.calculate(portfolioValuation);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTicker()).isEqualTo("AAPL");
        assertThat(result.getFirst().getExposureRate()).isEqualByComparingTo("100.00");
    }

    private CurrencyValuationTotals usdTotals(String purchaseAmount, String marketValue) {
        return new CurrencyValuationTotals(
                Currency.USD, new BigDecimal(purchaseAmount), new BigDecimal(marketValue),
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private HoldingValuation holdingValuation(
            Market market,
            String ticker,
            String marketValue
    ) {
        BigDecimal value = new BigDecimal(marketValue);

        return new HoldingValuation(
                market,
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
