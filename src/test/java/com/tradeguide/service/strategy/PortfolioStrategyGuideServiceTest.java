package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.*;
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

    @Mock
    private StrategyDecisionMaker strategyDecisionMaker;

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

        StrategySignal soxlSignal = new StrategySignal(
                new BigDecimal("25"),
                "상향 돌파",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 7)
                ),
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                4
        );

        StrategySignal aaplSignal = new StrategySignal(
                new BigDecimal("200"),
                "교차 없음",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 7)
                ),
                StrategyTrend.BELOW_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                null
        );

        StrategyDecision soxlDecision = new StrategyDecision(
                StrategyAction.HOLD,
                "테스트 보유 판단",
                soxlSignal
        );

        StrategyDecision aaplDecision = new StrategyDecision(
                StrategyAction.SELL,
                "테스트 보유 판단",
                aaplSignal
        );

        when(holdingService.getHoldings(1L, 10L))
                .thenReturn(List.of(soxlHolding, aaplHolding));
        when(strategyGuideService.getStrategySignal(Market.US, "SOXL"))
                .thenReturn(soxlSignal);
        when(strategyGuideService.getStrategySignal(Market.US, "AAPL"))
                .thenReturn(aaplSignal);
        when(strategyDecisionMaker.decideForHolding(soxlSignal))
                .thenReturn(soxlDecision);
        when(strategyDecisionMaker.decideForHolding(aaplSignal))
                .thenReturn(aaplDecision);

        List<AssetStrategyGuide> result =
                portfolioStrategyGuideService.getPortfolioStrategyGuides(
                        1L,
                        10L
                );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTicker()).isEqualTo("SOXL");
        assertThat(result.get(0).getStrategyDecision()).isSameAs(soxlDecision);

        assertThat(result.get(1).getTicker()).isEqualTo("AAPL");
        assertThat(result.get(1).getStrategyDecision()).isSameAs(aaplDecision);

        verify(holdingService).getHoldings(1L, 10L);
        verify(strategyGuideService).getStrategySignal(Market.US, "SOXL");
        verify(strategyGuideService).getStrategySignal(Market.US, "AAPL");
        verify(strategyDecisionMaker).decideForHolding(soxlSignal);
        verify(strategyDecisionMaker).decideForHolding(aaplSignal);
    }
}
