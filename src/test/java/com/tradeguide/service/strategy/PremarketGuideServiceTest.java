package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.PortfolioMarketDataPreference;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.StrategySignalEvent;
import com.tradeguide.domain.strategy.StrategyTrend;
import com.tradeguide.domain.strategy.PremarketGuideStatus;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.strategy.PremarketGuideResponse;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.PremarketGuideSnapshotRepository;
import com.tradeguide.service.asset.AssetDisplayNameResolver;
import com.tradeguide.service.market.UsEquityTradingCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PremarketGuideServiceTest {

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-14T13:00:00Z"),
            ZoneOffset.UTC
    );

    private PortfolioRepository portfolioRepository;
    private PremarketGuideSnapshotRepository snapshotRepository;
    private PortfolioStrategyGuideService portfolioStrategyGuideService;
    private PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService;
    private PremarketGuideService service;

    @BeforeEach
    void setUp() {
        portfolioRepository = mock(PortfolioRepository.class);
        snapshotRepository = mock(PremarketGuideSnapshotRepository.class);
        portfolioStrategyGuideService = mock(PortfolioStrategyGuideService.class);
        portfolioCandidateStrategyGuideService = mock(PortfolioCandidateStrategyGuideService.class);
        service = new PremarketGuideService(
                clock,
                portfolioRepository,
                snapshotRepository,
                portfolioStrategyGuideService,
                portfolioCandidateStrategyGuideService,
                new UsEquityTradingCalendar(),
                mock(AssetDisplayNameResolver.class)
        );
    }

    @Test
    void generatesAndStoresOneGuideForTheMarketDate() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(1L, 10L)).thenReturn(Optional.of(portfolio));
        when(portfolio.getMarketDataPreference())
                .thenReturn(PortfolioMarketDataPreference.unified(MarketDataProvider.TWELVE_DATA));
        when(snapshotRepository.findByPortfolio_IdAndGuideDate(10L, LocalDate.of(2026, 9, 14)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(1L, 10L))
                .thenReturn(new StrategyGuideBatch(List.of(guide("SOXL")), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(1L, 10L))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        PremarketGuideResponse result = service.generateToday(1L, 10L, false);

        assertThat(result.status()).isEqualTo(PremarketGuideStatus.COMPLETED);
        assertThat(result.guideDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(result.heldGuides()).extracting(response -> response.getTicker())
                .containsExactly("SOXL");
        assertThat(result.availableGuideCount()).isEqualTo(1);
        assertThat(result.marketDataProvider()).isEqualTo("TWELVE_DATA");
        verify(snapshotRepository).save(any());
    }

    @Test
    void reusesTodaysSnapshotWithoutCallingMarketDataWhenForceIsFalse() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(1L, 10L)).thenReturn(Optional.of(portfolio));

        com.tradeguide.domain.strategy.PremarketGuideSnapshot snapshot =
                mock(com.tradeguide.domain.strategy.PremarketGuideSnapshot.class);
        when(snapshot.getId()).thenReturn(1L);
        when(snapshot.getItems()).thenReturn(List.of());
        when(snapshot.getStatus()).thenReturn(PremarketGuideStatus.COMPLETED);
        when(snapshot.getGuideDate()).thenReturn(LocalDate.of(2026, 9, 14));
        when(snapshot.getGeneratedAt()).thenReturn(java.time.LocalDateTime.of(2026, 9, 14, 13, 0));
        when(snapshot.getHeldEmptyReason()).thenReturn(null);
        // V27 이전에 생성된 스냅샷을 흉내낸다: candle_market_data_provider 컬럼이 비어 있다.
        when(snapshot.getMarketDataProvider()).thenReturn(null);
        when(snapshotRepository.findByPortfolio_IdAndGuideDate(10L, LocalDate.of(2026, 9, 14)))
                .thenReturn(Optional.of(snapshot));

        PremarketGuideResponse result = service.generateToday(1L, 10L, false);

        assertThat(result.status()).isEqualTo(PremarketGuideStatus.COMPLETED);
        assertThat(result.marketDataProvider()).isNull();
        verifyNoInteractions(portfolioStrategyGuideService, portfolioCandidateStrategyGuideService);
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void forceRegeneratesTodaysSnapshotAndCallsBothGuideSources() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(1L, 10L)).thenReturn(Optional.of(portfolio));
        when(portfolio.getMarketDataPreference())
                .thenReturn(PortfolioMarketDataPreference.unified(MarketDataProvider.YAHOO_FINANCE));
        when(snapshotRepository.findByPortfolio_IdAndGuideDate(10L, LocalDate.of(2026, 9, 14)))
                .thenReturn(Optional.of(new com.tradeguide.domain.strategy.PremarketGuideSnapshot(
                        portfolio,
                        LocalDate.of(2026, 9, 14),
                        java.time.LocalDateTime.of(2026, 9, 14, 12, 0)
                )));
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(1L, 10L))
                .thenReturn(new StrategyGuideBatch(List.of(guide("SOXL")), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(1L, 10L))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        PremarketGuideResponse result = service.generateToday(1L, 10L, true);

        assertThat(result.status()).isEqualTo(PremarketGuideStatus.COMPLETED);
        assertThat(result.heldGuides()).extracting(response -> response.getTicker())
                .containsExactly("SOXL");
        verify(portfolioStrategyGuideService).getPortfolioStrategyGuides(1L, 10L);
        verify(portfolioCandidateStrategyGuideService).getCandidateStrategyGuides(1L, 10L);
        verify(snapshotRepository).save(any());
        assertThat(result.marketDataProvider()).isEqualTo("YAHOO_FINANCE");
    }

    @Test
    void aLaterProviderPreferenceChangeDoesNotRewriteAnExistingSnapshotsProvenance() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(1L, 10L)).thenReturn(Optional.of(portfolio));
        when(portfolio.getMarketDataPreference())
                .thenReturn(PortfolioMarketDataPreference.unified(MarketDataProvider.TOSS_SECURITIES));

        com.tradeguide.domain.strategy.PremarketGuideSnapshot existingSnapshot =
                mock(com.tradeguide.domain.strategy.PremarketGuideSnapshot.class);
        when(existingSnapshot.getId()).thenReturn(1L);
        when(existingSnapshot.getItems()).thenReturn(List.of());
        when(existingSnapshot.getStatus()).thenReturn(PremarketGuideStatus.COMPLETED);
        when(existingSnapshot.getGuideDate()).thenReturn(LocalDate.of(2026, 9, 14));
        when(existingSnapshot.getGeneratedAt()).thenReturn(java.time.LocalDateTime.of(2026, 9, 14, 13, 0));
        when(existingSnapshot.getHeldEmptyReason()).thenReturn(null);
        when(existingSnapshot.getMarketDataProvider()).thenReturn(MarketDataProvider.TWELVE_DATA);
        when(snapshotRepository.findByPortfolio_IdAndGuideDate(10L, LocalDate.of(2026, 9, 14)))
                .thenReturn(Optional.of(existingSnapshot));

        PremarketGuideResponse result = service.generateToday(1L, 10L, false);

        assertThat(result.marketDataProvider()).isEqualTo("TWELVE_DATA");
        verify(existingSnapshot, never()).replaceResults(any(), any(), any(), any(), any());
    }

    private AssetStrategyGuide guide(String ticker) {
        StrategySignal signal = new StrategySignal(
                new BigDecimal("117.10"),
                "완료 주봉 기준 장기 이동평균 위에 있습니다.",
                new StrategyMetadata("TRACK_A_SMA", "1", LocalDate.of(2026, 9, 11)),
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                null
        );
        return new AssetStrategyGuide(
                Market.US,
                ticker,
                new com.tradeguide.domain.strategy.StrategyDecision(
                        StrategyAction.HOLD,
                        signal.getReason(),
                        signal
                )
        );
    }
}
