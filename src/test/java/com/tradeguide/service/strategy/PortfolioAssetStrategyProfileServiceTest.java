package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioAssetStrategyProfile;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.PortfolioAssetNotHeldException;
import com.tradeguide.exception.PortfolioAssetStrategyProfileNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.exception.UnsupportedInvestmentTrackException;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.repository.strategy.PortfolioAssetStrategyProfileRepository;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.indicator.SimpleMovingAverageCalculator;
import com.tradeguide.service.strategy.tracka.WeeklyMaCrossoverStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioAssetStrategyProfileServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private HoldingService holdingService;

    @Mock
    private PortfolioAssetStrategyProfileRepository portfolioAssetStrategyProfileRepository;

    @Mock
    private AssetProfileRepository assetProfileRepository;

    private final StrategySelector strategySelector = new StrategySelector(
            List.of(new WeeklyMaCrossoverStrategy(new SimpleMovingAverageCalculator()))
    );

    private PortfolioAssetStrategyProfileService service;

    private Member member;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        service = new PortfolioAssetStrategyProfileService(
                portfolioRepository,
                holdingService,
                portfolioAssetStrategyProfileRepository,
                assetProfileRepository,
                strategySelector,
                FIXED_CLOCK
        );

        member = new Member("owner@example.com", "owner");
        portfolio = new Portfolio(member, "테스트 포트폴리오");
    }

    @Test
    void throwsNotFoundWhenPortfolioDoesNotBelongToMember() {
        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHeldAssetStrategyProfiles(1L, 10L))
                .isInstanceOf(PortfolioNotFoundException.class);

        assertThatThrownBy(() -> service.upsert(1L, 10L, Market.US, "SOXL", InvestmentTrack.TRACK_B))
                .isInstanceOf(PortfolioNotFoundException.class);

        assertThatThrownBy(() -> service.delete(1L, 10L, Market.US, "SOXL"))
                .isInstanceOf(PortfolioNotFoundException.class);

        verify(holdingService, never()).getHoldings(any(), any());
    }

    @Test
    void createsOverrideWhenAssetIsHeld() {
        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(holdingService.getHoldings(1L, 10L)).thenReturn(List.of(
                new Holding(Market.US, "SOXL", new BigDecimal("10"), new BigDecimal("20"))
        ));
        when(portfolioAssetStrategyProfileRepository
                .findByPortfolio_IdAndMarketAndTicker(10L, Market.US, "SOXL"))
                .thenReturn(Optional.empty());
        when(assetProfileRepository.findByMarketAndTicker(Market.US, "SOXL"))
                .thenReturn(Optional.of(new AssetProfile(Market.US, "SOXL", InvestmentTrack.TRACK_A)));
        when(portfolioAssetStrategyProfileRepository.save(any(PortfolioAssetStrategyProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioAssetStrategyProfileService.PortfolioAssetStrategyProfileResult result =
                service.upsert(1L, 10L, Market.US, "SOXL", InvestmentTrack.TRACK_A);

        assertThat(result.overrideTrack()).isEqualTo(InvestmentTrack.TRACK_A);
        assertThat(result.globalTrack()).isEqualTo(InvestmentTrack.TRACK_A);
        assertThat(result.updatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 11, 0, 0));
    }

    @Test
    void updatesExistingOverrideInsteadOfCreatingDuplicate() {
        PortfolioAssetStrategyProfile existing = new PortfolioAssetStrategyProfile(
                portfolio,
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_B,
                LocalDateTime.of(2026, 9, 1, 0, 0)
        );

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(holdingService.getHoldings(1L, 10L)).thenReturn(List.of(
                new Holding(Market.US, "SOXL", new BigDecimal("10"), new BigDecimal("20"))
        ));
        when(portfolioAssetStrategyProfileRepository
                .findByPortfolio_IdAndMarketAndTicker(10L, Market.US, "SOXL"))
                .thenReturn(Optional.of(existing));
        when(assetProfileRepository.findByMarketAndTicker(Market.US, "SOXL"))
                .thenReturn(Optional.empty());
        when(portfolioAssetStrategyProfileRepository.save(any(PortfolioAssetStrategyProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioAssetStrategyProfileService.PortfolioAssetStrategyProfileResult result =
                service.upsert(1L, 10L, Market.US, "SOXL", InvestmentTrack.TRACK_A);

        assertThat(result.overrideTrack()).isEqualTo(InvestmentTrack.TRACK_A);
        assertThat(result.globalTrack()).isNull();
        assertThat(existing.getInvestmentTrack()).isEqualTo(InvestmentTrack.TRACK_A);
        assertThat(existing.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 11, 0, 0));
    }

    @Test
    void rejectsUpsertWhenTrackHasNoSupportedStrategy() {
        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(holdingService.getHoldings(1L, 10L)).thenReturn(List.of(
                new Holding(Market.US, "SOXL", new BigDecimal("10"), new BigDecimal("20"))
        ));

        assertThatThrownBy(() ->
                service.upsert(1L, 10L, Market.US, "SOXL", InvestmentTrack.TRACK_B)
        ).isInstanceOf(UnsupportedInvestmentTrackException.class);

        verify(portfolioAssetStrategyProfileRepository, never())
                .save(any(PortfolioAssetStrategyProfile.class));
    }

    @Test
    void rejectsOverrideWhenAssetIsNotCurrentlyHeld() {
        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(holdingService.getHoldings(1L, 10L)).thenReturn(List.of(
                new Holding(Market.US, "AAPL", new BigDecimal("5"), new BigDecimal("180"))
        ));

        assertThatThrownBy(() ->
                service.upsert(1L, 10L, Market.US, "SOXL", InvestmentTrack.TRACK_B)
        ).isInstanceOf(PortfolioAssetNotHeldException.class);

        verify(portfolioAssetStrategyProfileRepository, never())
                .save(any(PortfolioAssetStrategyProfile.class));
    }

    @Test
    void deletesExistingOverride() {
        PortfolioAssetStrategyProfile existing = new PortfolioAssetStrategyProfile(
                portfolio,
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_B,
                LocalDateTime.of(2026, 9, 1, 0, 0)
        );

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(portfolioAssetStrategyProfileRepository
                .findByPortfolio_IdAndMarketAndTicker(10L, Market.US, "SOXL"))
                .thenReturn(Optional.of(existing));

        service.delete(1L, 10L, Market.US, "SOXL");

        verify(portfolioAssetStrategyProfileRepository).delete(existing);
    }

    @Test
    void throwsNotFoundWhenDeletingMissingOverride() {
        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(portfolioAssetStrategyProfileRepository
                .findByPortfolio_IdAndMarketAndTicker(10L, Market.US, "SOXL"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 10L, Market.US, "SOXL"))
                .isInstanceOf(PortfolioAssetStrategyProfileNotFoundException.class);
    }

    @Test
    void getHeldAssetStrategyProfilesCombinesOverrideAndGlobalState() {
        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(holdingService.getHoldings(1L, 10L)).thenReturn(List.of(
                new Holding(Market.US, "SOXL", new BigDecimal("10"), new BigDecimal("20")),
                new Holding(Market.US, "AAPL", new BigDecimal("5"), new BigDecimal("180"))
        ));

        PortfolioAssetStrategyProfile soxlOverride = new PortfolioAssetStrategyProfile(
                portfolio,
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_B,
                LocalDateTime.of(2026, 9, 1, 0, 0)
        );

        when(portfolioAssetStrategyProfileRepository.findAllByPortfolio_Id(10L))
                .thenReturn(List.of(soxlOverride));
        when(assetProfileRepository.findByMarketAndTicker(Market.US, "SOXL"))
                .thenReturn(Optional.of(new AssetProfile(Market.US, "SOXL", InvestmentTrack.TRACK_A)));
        when(assetProfileRepository.findByMarketAndTicker(Market.US, "AAPL"))
                .thenReturn(Optional.empty());

        List<PortfolioAssetStrategyProfileService.PortfolioAssetStrategyProfileResult> results =
                service.getHeldAssetStrategyProfiles(1L, 10L);

        assertThat(results).hasSize(2);

        PortfolioAssetStrategyProfileService.PortfolioAssetStrategyProfileResult soxl = results.stream()
                .filter(result -> result.ticker().equals("SOXL"))
                .findFirst()
                .orElseThrow();
        assertThat(soxl.overrideTrack()).isEqualTo(InvestmentTrack.TRACK_B);
        assertThat(soxl.globalTrack()).isEqualTo(InvestmentTrack.TRACK_A);

        PortfolioAssetStrategyProfileService.PortfolioAssetStrategyProfileResult aapl = results.stream()
                .filter(result -> result.ticker().equals("AAPL"))
                .findFirst()
                .orElseThrow();
        assertThat(aapl.overrideTrack()).isNull();
        assertThat(aapl.globalTrack()).isNull();
    }
}
