package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.StrategySignalEvent;
import com.tradeguide.domain.strategy.StrategyTrend;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.strategy.AssetProfileRepository;
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
class PortfolioCandidateStrategyGuideServiceTest {

    @Mock
    private HoldingService holdingService;

    @Mock
    private AssetProfileRepository assetProfileRepository;

    @Mock
    private StrategyGuideService strategyGuideService;

    @Mock
    private StrategyDecisionMaker strategyDecisionMaker;

    @InjectMocks
    private PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService;

    @Test
    void getsGuidesForUnheldTrackACandidates() {
        Holding soxlHolding = new Holding(
                Market.US,
                "SOXL",
                new BigDecimal("10"),
                new BigDecimal("20")
        );

        AssetProfile soxlProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );

        AssetProfile tqqqProfile = new AssetProfile(
                Market.US,
                "TQQQ",
                InvestmentTrack.TRACK_A
        );

        StrategySignal tqqqSignal = new StrategySignal(
                new BigDecimal("90"),
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

        StrategyDecision tqqqDecision = new StrategyDecision(
                StrategyAction.BUY,
                "테스트 후보 판단",
                tqqqSignal
        );

        when(holdingService.getHoldings(1L, 10L))
                .thenReturn(List.of(soxlHolding));
        when(assetProfileRepository.findAllByInvestmentTrack(InvestmentTrack.TRACK_A))
                .thenReturn(List.of(soxlProfile, tqqqProfile));
        when(strategyGuideService.getStrategySignal(Market.US, "TQQQ"))
                .thenReturn(tqqqSignal);
        when(strategyDecisionMaker.decideForCandidate(tqqqSignal))
                .thenReturn(tqqqDecision);

        List<AssetStrategyGuide> result =
                portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(
                        1L,
                        10L
                );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTicker()).isEqualTo("TQQQ");
        assertThat(result.get(0).getStrategyDecision()).isSameAs(tqqqDecision);

        verify(holdingService).getHoldings(1L, 10L);
        verify(assetProfileRepository)
                .findAllByInvestmentTrack(InvestmentTrack.TRACK_A);
        verify(strategyGuideService).getStrategySignal(Market.US, "TQQQ");
        verify(strategyDecisionMaker).decideForCandidate(tqqqSignal);
    }
}