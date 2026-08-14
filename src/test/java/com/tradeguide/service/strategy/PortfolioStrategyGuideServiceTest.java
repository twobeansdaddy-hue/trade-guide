package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.*;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
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
import static org.mockito.Mockito.never;
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

        StrategyGuideBatch result =
                portfolioStrategyGuideService.getPortfolioStrategyGuides(
                        1L,
                        10L
                );

        assertThat(result.getGuides()).hasSize(2);
        assertThat(result.getGuides().get(0).getTicker()).isEqualTo("SOXL");
        assertThat(result.getGuides().get(0).getStrategyDecision()).isSameAs(soxlDecision);

        assertThat(result.getGuides().get(1).getTicker()).isEqualTo("AAPL");
        assertThat(result.getGuides().get(1).getStrategyDecision()).isSameAs(aaplDecision);

        assertThat(result.getUnavailableAssets()).isEmpty();

        verify(holdingService).getHoldings(1L, 10L);
        verify(strategyGuideService).getStrategySignal(Market.US, "SOXL");
        verify(strategyGuideService).getStrategySignal(Market.US, "AAPL");
        verify(strategyDecisionMaker).decideForHolding(soxlSignal);
        verify(strategyDecisionMaker).decideForHolding(aaplSignal);
    }

    @Test
    void returnsAvailableGuidesWhenOneHoldingMarketDataIsUnavailable() {
        Holding soxlHolding = new Holding(
                Market.US,
                "SOXL",
                new BigDecimal("10"),
                new BigDecimal("20")
        );

        Holding tqqqHolding = new Holding(
                Market.US,
                "TQQQ",
                new BigDecimal("5"),
                new BigDecimal("90")
        );

        StrategySignal soxlSignal = new StrategySignal(
                new BigDecimal("25"),
                "테스트 시장 신호",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 10)
                ),
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                2
        );

        StrategyDecision soxlDecision = new StrategyDecision(
                StrategyAction.HOLD,
                "테스트 보유 판단",
                soxlSignal
        );

        when(holdingService.getHoldings(1L, 10L))
                .thenReturn(List.of(soxlHolding, tqqqHolding));
        when(strategyGuideService.getStrategySignal(Market.US, "SOXL"))
                .thenReturn(soxlSignal);
        when(strategyGuideService.getStrategySignal(Market.US, "TQQQ"))
                .thenThrow(new MarketDataUnavailableException(
                        "시장 데이터 조회에 실패했습니다."
                ));
        when(strategyDecisionMaker.decideForHolding(soxlSignal))
                .thenReturn(soxlDecision);

        StrategyGuideBatch result =
                portfolioStrategyGuideService.getPortfolioStrategyGuides(
                        1L,
                        10L
                );

        assertThat(result.getGuides()).hasSize(1);
        assertThat(result.getGuides().get(0).getTicker()).isEqualTo("SOXL");
        assertThat(result.getGuides().get(0).getStrategyDecision())
                .isSameAs(soxlDecision);

        assertThat(result.getUnavailableAssets()).hasSize(1);
        assertThat(result.getUnavailableAssets().get(0).getMarket())
                .isEqualTo(Market.US);
        assertThat(result.getUnavailableAssets().get(0).getTicker())
                .isEqualTo("TQQQ");
        assertThat(result.getUnavailableAssets().get(0).getMessage())
                .isEqualTo("시장 데이터 조회에 실패했습니다.");

        verify(strategyGuideService).getStrategySignal(Market.US, "SOXL");
        verify(strategyGuideService).getStrategySignal(Market.US, "TQQQ");
        verify(strategyDecisionMaker).decideForHolding(soxlSignal);
    }

    @Test
    void stopsRemainingRequestsWhenMarketDataRateLimitIsExceeded() {
        Holding soxlHolding = new Holding(
                Market.US,
                "SOXL",
                new BigDecimal("10"),
                new BigDecimal("20")
        );

        Holding tqqqHolding = new Holding(
                Market.US,
                "TQQQ",
                new BigDecimal("5"),
                new BigDecimal("90")
        );

        Holding uproHolding = new Holding(
                Market.US,
                "UPRO",
                new BigDecimal("3"),
                new BigDecimal("100")
        );

        StrategySignal soxlSignal = new StrategySignal(
                new BigDecimal("25"),
                "테스트 시장 신호",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 10)
                ),
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                2
        );

        StrategyDecision soxlDecision = new StrategyDecision(
                StrategyAction.HOLD,
                "테스트 보유 판단",
                soxlSignal
        );

        when(holdingService.getHoldings(1L, 10L))
                .thenReturn(List.of(soxlHolding, tqqqHolding, uproHolding));
        when(strategyGuideService.getStrategySignal(Market.US, "SOXL"))
                .thenReturn(soxlSignal);
        when(strategyGuideService.getStrategySignal(Market.US, "TQQQ"))
                .thenThrow(new MarketDataRateLimitExceededException(
                        "시장 데이터 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
                        new RuntimeException()
                ));
        when(strategyDecisionMaker.decideForHolding(soxlSignal))
                .thenReturn(soxlDecision);

        StrategyGuideBatch result =
                portfolioStrategyGuideService.getPortfolioStrategyGuides(
                        1L,
                        10L
                );

        assertThat(result.getGuides()).hasSize(1);
        assertThat(result.getGuides().get(0).getTicker()).isEqualTo("SOXL");

        assertThat(result.getUnavailableAssets())
                .extracting(UnavailableAsset::getTicker)
                .containsExactly("TQQQ", "UPRO");
        assertThat(result.getUnavailableAssets().get(0).getMessage())
                .isEqualTo("시장 데이터 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        assertThat(result.getUnavailableAssets().get(1).getMessage())
                .isEqualTo("시장 데이터 요청 제한으로 조회하지 못했습니다.");

        verify(strategyGuideService).getStrategySignal(Market.US, "SOXL");
        verify(strategyGuideService).getStrategySignal(Market.US, "TQQQ");
        verify(strategyGuideService, never())
                .getStrategySignal(Market.US, "UPRO");
    }
}
