package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.*;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
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
import static org.mockito.Mockito.never;
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

        StrategyGuideBatch result =
                portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(
                        1L,
                        10L
                );

        assertThat(result.getGuides()).hasSize(1);
        assertThat(result.getGuides().get(0).getTicker()).isEqualTo("TQQQ");
        assertThat(result.getGuides().get(0).getStrategyDecision()).isSameAs(tqqqDecision);
        assertThat(result.getUnavailableAssets()).isEmpty();

        verify(holdingService).getHoldings(1L, 10L);
        verify(assetProfileRepository)
                .findAllByInvestmentTrack(InvestmentTrack.TRACK_A);
        verify(strategyGuideService).getStrategySignal(Market.US, "TQQQ");
        verify(strategyDecisionMaker).decideForCandidate(tqqqSignal);
    }

    @Test
    void returnsAvailableGuidesWhenOneCandidateMarketDataIsUnavailable() {
        AssetProfile tqqqProfile = new AssetProfile(
                Market.US,
                "TQQQ",
                InvestmentTrack.TRACK_A
        );

        AssetProfile uproProfile = new AssetProfile(
                Market.US,
                "UPRO",
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
                .thenReturn(List.of());
        when(assetProfileRepository.findAllByInvestmentTrack(InvestmentTrack.TRACK_A))
                .thenReturn(List.of(tqqqProfile, uproProfile));
        when(strategyGuideService.getStrategySignal(Market.US, "TQQQ"))
                .thenReturn(tqqqSignal);
        when(strategyGuideService.getStrategySignal(Market.US, "UPRO"))
                .thenThrow(new MarketDataUnavailableException(
                        "시장 데이터 조회에 실패했습니다."
                ));
        when(strategyDecisionMaker.decideForCandidate(tqqqSignal))
                .thenReturn(tqqqDecision);

        StrategyGuideBatch result =
                portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(
                        1L,
                        10L
                );

        assertThat(result.getGuides()).hasSize(1);
        assertThat(result.getGuides().get(0).getTicker()).isEqualTo("TQQQ");
        assertThat(result.getUnavailableAssets()).hasSize(1);
        assertThat(result.getUnavailableAssets().get(0).getTicker())
                .isEqualTo("UPRO");
        assertThat(result.getUnavailableAssets().get(0).getMessage())
                .isEqualTo("시장 데이터 조회에 실패했습니다.");

        verify(strategyGuideService).getStrategySignal(Market.US, "TQQQ");
        verify(strategyGuideService).getStrategySignal(Market.US, "UPRO");
        verify(strategyDecisionMaker).decideForCandidate(tqqqSignal);
    }

    @Test
    void stopsRemainingRequestsWhenCandidateMarketDataRateLimitIsExceeded() {
        AssetProfile tqqqProfile = new AssetProfile(
                Market.US,
                "TQQQ",
                InvestmentTrack.TRACK_A
        );

        AssetProfile uproProfile = new AssetProfile(
                Market.US,
                "UPRO",
                InvestmentTrack.TRACK_A
        );

        AssetProfile spxlProfile = new AssetProfile(
                Market.US,
                "SPXL",
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
                .thenReturn(List.of());
        when(assetProfileRepository.findAllByInvestmentTrack(InvestmentTrack.TRACK_A))
                .thenReturn(List.of(tqqqProfile, uproProfile, spxlProfile));
        when(strategyGuideService.getStrategySignal(Market.US, "TQQQ"))
                .thenReturn(tqqqSignal);
        when(strategyGuideService.getStrategySignal(Market.US, "UPRO"))
                .thenThrow(new MarketDataRateLimitExceededException(
                        "시장 데이터 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
                        new RuntimeException()
                ));
        when(strategyDecisionMaker.decideForCandidate(tqqqSignal))
                .thenReturn(tqqqDecision);

        StrategyGuideBatch result =
                portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(
                        1L,
                        10L
                );

        assertThat(result.getGuides()).hasSize(1);
        assertThat(result.getGuides().get(0).getTicker()).isEqualTo("TQQQ");

        assertThat(result.getUnavailableAssets())
                .extracting(UnavailableAsset::getTicker)
                .containsExactly("UPRO", "SPXL");
        assertThat(result.getUnavailableAssets().get(0).getMessage())
                .isEqualTo("시장 데이터 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        assertThat(result.getUnavailableAssets().get(1).getMessage())
                .isEqualTo("시장 데이터 요청 제한으로 조회하지 못했습니다.");

        verify(strategyGuideService).getStrategySignal(Market.US, "TQQQ");
        verify(strategyGuideService).getStrategySignal(Market.US, "UPRO");
        verify(strategyGuideService, never())
                .getStrategySignal(Market.US, "SPXL");
    }
}
