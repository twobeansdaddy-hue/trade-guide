package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.MarketCandle;
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
import com.tradeguide.domain.strategy.GuideInputEvidenceStatus;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.strategy.PremarketGuideResponse;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.PremarketGuideSnapshotRepository;
import com.tradeguide.service.asset.AssetDisplayNameResolver;
import com.tradeguide.service.market.UsEquityTradingCalendar;
import com.tradeguide.service.market.CompletedWeeklyCandleCache;
import com.tradeguide.service.market.CompletedWeeklyCandleFilter;
import com.tradeguide.service.market.MarketCandleDigest;
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
    private CompletedWeeklyCandleCache completedWeeklyCandleCache;
    private CompletedWeeklyCandleFilter completedWeeklyCandleFilter;
    private MarketCandleDigest marketCandleDigest;
    private PremarketGuideService service;

    @BeforeEach
    void setUp() {
        portfolioRepository = mock(PortfolioRepository.class);
        snapshotRepository = mock(PremarketGuideSnapshotRepository.class);
        portfolioStrategyGuideService = mock(PortfolioStrategyGuideService.class);
        portfolioCandidateStrategyGuideService = mock(PortfolioCandidateStrategyGuideService.class);
        completedWeeklyCandleCache = mock(CompletedWeeklyCandleCache.class);
        completedWeeklyCandleFilter = mock(CompletedWeeklyCandleFilter.class);
        marketCandleDigest = mock(MarketCandleDigest.class);
        service = new PremarketGuideService(
                clock,
                portfolioRepository,
                snapshotRepository,
                portfolioStrategyGuideService,
                portfolioCandidateStrategyGuideService,
                new UsEquityTradingCalendar(),
                mock(AssetDisplayNameResolver.class),
                completedWeeklyCandleCache,
                completedWeeklyCandleFilter,
                marketCandleDigest
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
        assertThat(result.inputEvidenceStatus()).isEqualTo("UNVERIFIED");
        var savedSnapshot = org.mockito.ArgumentCaptor.forClass(
                com.tradeguide.domain.strategy.PremarketGuideSnapshot.class);
        verify(snapshotRepository).save(savedSnapshot.capture());
        assertThat(savedSnapshot.getValue().getInputAudit().getEvidenceStatus())
                .isEqualTo(GuideInputEvidenceStatus.UNVERIFIED);
        assertThat(savedSnapshot.getValue().getInputAudit().getRecordedAt())
                .isEqualTo(clock.instant());
        assertThat(savedSnapshot.getValue().getInputAudit().getResponseReceivedAt()).isNull();
        assertThat(savedSnapshot.getValue().getInputAudit().getInputSha256()).isNull();
    }

    @Test
    void storesOnlyMatchingCompletedCandleEvidenceWithoutVerifyingWholeGuide() {
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
        Instant loadedAt = Instant.parse("2026-09-11T21:00:00Z");
        MarketCandle candle = new MarketCandle(Market.US, "SOXL", LocalDate.of(2026, 9, 11),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 100L);
        when(completedWeeklyCandleCache.findObservation("TWELVE_DATA", Market.US, "SOXL", 101))
                .thenReturn(Optional.of(new CompletedWeeklyCandleCache.CandleLoadObservation(
                        List.of(candle), loadedAt)));
        when(completedWeeklyCandleFilter.filter(List.of(candle))).thenReturn(List.of(candle));
        when(marketCandleDigest.sha256(eq(MarketDataProvider.TWELVE_DATA), any(), eq(List.of(candle))))
                .thenReturn("a".repeat(64));

        service.generateToday(1L, 10L, false);

        var saved = org.mockito.ArgumentCaptor.forClass(
                com.tradeguide.domain.strategy.PremarketGuideSnapshot.class);
        verify(snapshotRepository).save(saved.capture());
        assertThat(saved.getValue().getCandleEvidence()).hasSize(1);
        assertThat(saved.getValue().getCandleEvidence().get(0).getLoadCompletedAt()).isEqualTo(loadedAt);
        assertThat(saved.getValue().getCandleEvidence().get(0).getCandleSha256()).isEqualTo("a".repeat(64));
        assertThat(saved.getValue().getInputAudit().getEvidenceStatus())
                .isEqualTo(GuideInputEvidenceStatus.UNVERIFIED);
        assertThat(saved.getValue().getInputAudit().getInputSha256()).isNull();
        assertThat(saved.getValue().getInputAudit().getMissingReasons())
                .doesNotContain("CANDLE_EVIDENCE_INCOMPLETE");
    }

    @Test
    void doesNotRecordCandleEvidenceWhenCacheDateDisagreesWithGuide() {
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
        MarketCandle staleCandle = new MarketCandle(Market.US, "SOXL", LocalDate.of(2026, 9, 4),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 100L);
        when(completedWeeklyCandleCache.findObservation("TWELVE_DATA", Market.US, "SOXL", 101))
                .thenReturn(Optional.of(new CompletedWeeklyCandleCache.CandleLoadObservation(
                        List.of(staleCandle), Instant.parse("2026-09-11T21:00:00Z"))));
        when(completedWeeklyCandleFilter.filter(List.of(staleCandle))).thenReturn(List.of(staleCandle));

        service.generateToday(1L, 10L, false);

        var saved = org.mockito.ArgumentCaptor.forClass(
                com.tradeguide.domain.strategy.PremarketGuideSnapshot.class);
        verify(snapshotRepository).save(saved.capture());
        assertThat(saved.getValue().getCandleEvidence()).isEmpty();
        assertThat(saved.getValue().getInputAudit().getMissingReasons())
                .contains("CANDLE_EVIDENCE_INCOMPLETE");
        verifyNoInteractions(marketCandleDigest);
    }

    @Test
    void forceRegenerationRemovesEvidenceThatCannotBeObservedAgain() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(1L, 10L)).thenReturn(Optional.of(portfolio));
        when(portfolio.getMarketDataPreference())
                .thenReturn(PortfolioMarketDataPreference.unified(MarketDataProvider.TWELVE_DATA));
        var snapshot = new com.tradeguide.domain.strategy.PremarketGuideSnapshot(
                portfolio, LocalDate.of(2026, 9, 14), java.time.LocalDateTime.of(2026, 9, 14, 12, 0));
        snapshot.replaceCandleEvidence(List.of(new com.tradeguide.domain.strategy.PremarketGuideCandleEvidence(
                com.tradeguide.domain.strategy.PremarketGuideScope.HELD, Market.US, "SOXL",
                MarketDataProvider.TWELVE_DATA, Instant.parse("2026-09-11T21:00:00Z"),
                "a".repeat(64), 40, LocalDate.of(2026, 9, 11))));
        when(snapshotRepository.findByPortfolio_IdAndGuideDate(10L, LocalDate.of(2026, 9, 14)))
                .thenReturn(Optional.of(snapshot));
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(1L, 10L))
                .thenReturn(new StrategyGuideBatch(List.of(guide("SOXL")), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(1L, 10L))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));
        when(completedWeeklyCandleCache.findObservation("TWELVE_DATA", Market.US, "SOXL", 101))
                .thenReturn(Optional.empty());

        service.generateToday(1L, 10L, true);

        assertThat(snapshot.getCandleEvidence()).isEmpty();
        assertThat(snapshot.getInputAudit().getEvidenceStatus())
                .isEqualTo(GuideInputEvidenceStatus.UNVERIFIED);
        assertThat(snapshot.getInputAudit().getMissingReasons())
                .contains("CANDLE_EVIDENCE_INCOMPLETE");
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
        assertThat(result.inputEvidenceStatus()).isNull();
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
        assertThat(result.inputEvidenceStatus()).isEqualTo("UNVERIFIED");
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
