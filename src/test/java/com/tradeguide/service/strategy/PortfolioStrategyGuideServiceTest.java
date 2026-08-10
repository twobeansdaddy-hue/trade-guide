package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.service.holding.HoldingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioStrategyGuideServiceTest {

    @Mock
    private HoldingService holdingService;

    @Mock
    private StrategyGuideService strategyGuideService;

    @InjectMocks
    private PortfolioStrategyGuideService portfolioStrategyGuideService;

    @Test
    void getsStrategyGuidesForPortfolioHoldings() {
        Holding soxlHolding = new Holding(
                Market.US,
                "SOXL",
                new BigDecimal("10"),
                new BigDecimal("20")
        );
        Holding aaplHolding = new Holding(
                Market.US,
                "AAPL",
                new BigDecimal("5"),
                new BigDecimal("180")
        );

        StrategyDecision soxlDecision = new StrategyDecision(
                StrategyAction.BUY,
                new BigDecimal("25"),
                "상향 돌파",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 7)
                )
        );
        StrategyDecision aaplDecision = new StrategyDecision(
                StrategyAction.HOLD,
                new BigDecimal("200"),
                "교차 없음",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 7)
                )
        );

        when(holdingService.getHoldings(1L, 10L))
                .thenReturn(List.of(soxlHolding, aaplHolding));

        when(strategyGuideService.getStrategyDecision(Market.US, "SOXL"))
                .thenReturn(soxlDecision);
        when(strategyGuideService.getStrategyDecision(Market.US, "AAPL"))
                .thenReturn(aaplDecision);

        List<AssetStrategyGuide> result =
                portfolioStrategyGuideService.getPortfolioStrategyGuides(
                        1L,
                        10L
                );

        assertThat(result).hasSize(2);

        assertThat(result.get(0).getTicker()).isEqualTo("SOXL");
        assertThat(result.get(0).getStrategyDecision())
                .isSameAs(soxlDecision);

        assertThat(result.get(1).getTicker()).isEqualTo("AAPL");
        assertThat(result.get(1).getStrategyDecision())
                .isSameAs(aaplDecision);

        verify(holdingService).getHoldings(1L, 10L);
        verify(strategyGuideService)
                .getStrategyDecision(Market.US, "SOXL");
        verify(strategyGuideService)
                .getStrategyDecision(Market.US, "AAPL");
    }
}