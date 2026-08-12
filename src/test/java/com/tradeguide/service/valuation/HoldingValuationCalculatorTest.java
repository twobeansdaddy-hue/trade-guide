package com.tradeguide.service.valuation;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.HoldingValuation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldingValuationCalculatorTest {

    private final HoldingValuationCalculator calculator =
            new HoldingValuationCalculator();

    @Test
    void calculatesHoldingValuation() {
        // given
        Holding holding = new Holding(
                Market.US,
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("100")
        );
        MarketPrice marketPrice = new MarketPrice(
                Market.US,
                "AAPL",
                new BigDecimal("110"),
                Instant.parse("2026-08-03T00:00:00Z")
        );

        // when
        HoldingValuation result = calculator.calculate(holding, marketPrice);

        // then
        assertThat(result.getPurchaseAmount())
                .isEqualByComparingTo("1000");
        assertThat(result.getMarketValue())
                .isEqualByComparingTo("1100");
        assertThat(result.getUnrealizedProfitLoss())
                .isEqualByComparingTo("100");
        assertThat(result.getReturnRate())
                .isEqualByComparingTo("10");
    }

    @Test
    void throwsExceptionWhenHoldingAndMarketPriceTickersDiffer() {
        // given
        Holding holding = new Holding(
                Market.US,
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("100")
        );
        MarketPrice marketPrice = new MarketPrice(
                Market.US,
                "MSFT",
                new BigDecimal("110"),
                Instant.parse("2026-08-03T00:00:00Z")
        );

        // when & then
        assertThatThrownBy(() -> calculator.calculate(holding, marketPrice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("보유 종목과 현재가 종목이 일치하지 않습니다.");
    }
}