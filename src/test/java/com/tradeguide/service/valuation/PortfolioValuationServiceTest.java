package com.tradeguide.service.valuation;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.market.MarketPriceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioValuationServiceTest {

    @Mock
    private HoldingService holdingService;

    @Mock
    private MarketPriceProvider marketPriceProvider;

    @Mock
    private HoldingValuationCalculator holdingValuationCalculator;

    @Mock
    private PortfolioValuationCalculator portfolioValuationCalculator;

    @InjectMocks
    private PortfolioValuationService portfolioValuationService;

    @Test
    void getsPortfolioValuation() {
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
                Instant.parse("2026-08-04T00:00:00Z")
        );
        HoldingValuation holdingValuation = new HoldingValuation(
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
        PortfolioValuation expected = new PortfolioValuation(
                List.of(holdingValuation),
                new BigDecimal("1000"),
                new BigDecimal("1100"),
                new BigDecimal("100"),
                new BigDecimal("10")
        );

        when(holdingService.getHoldings(10L, 100L))
                .thenReturn(List.of(holding));
        when(marketPriceProvider.getCurrentPrice(Market.US, "AAPL"))
                .thenReturn(marketPrice);
        when(holdingValuationCalculator.calculate(holding, marketPrice))
                .thenReturn(holdingValuation);
        when(portfolioValuationCalculator.calculate(List.of(holdingValuation)))
                .thenReturn(expected);

        // when
        PortfolioValuation result =
                portfolioValuationService.getPortfolioValuation(10L, 100L);

        // then
        assertThat(result).isSameAs(expected);
        verify(holdingService).getHoldings(10L, 100L);
        verify(marketPriceProvider).getCurrentPrice(Market.US, "AAPL");
        verify(holdingValuationCalculator).calculate(holding, marketPrice);
        verify(portfolioValuationCalculator)
                .calculate(List.of(holdingValuation));
    }
}