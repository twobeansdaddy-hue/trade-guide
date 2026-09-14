package com.tradeguide.service.strategy;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioCandidateAsset;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.PortfolioCandidateAssetAlreadyExistsException;
import com.tradeguide.exception.PortfolioCandidateAssetNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.PortfolioCandidateAssetRepository;
import com.tradeguide.service.asset.AssetListingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class PortfolioCandidateAssetServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioCandidateAssetRepository portfolioCandidateAssetRepository;

    @Mock
    private AssetListingService assetListingService;

    private PortfolioCandidateAssetService service;

    private Member member;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        service = new PortfolioCandidateAssetService(
                portfolioRepository,
                portfolioCandidateAssetRepository,
                assetListingService,
                FIXED_CLOCK
        );

        member = new Member("owner@example.com", "owner");
        portfolio = new Portfolio(member, "테스트 포트폴리오");
    }

    @Test
    void throwsNotFoundWhenPortfolioDoesNotBelongToMember() {
        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, 10L, Market.US, "SOXL", "디렉시온 반도체 불3배"))
                .isInstanceOf(PortfolioNotFoundException.class);

        assertThatThrownBy(() -> service.getCandidateAssets(1L, 10L))
                .isInstanceOf(PortfolioNotFoundException.class);

        assertThatThrownBy(() -> service.delete(1L, 10L, Market.US, "SOXL"))
                .isInstanceOf(PortfolioNotFoundException.class);

        verify(assetListingService, never()).ensureActiveListingForTrade(any(), any());
    }

    @Test
    void createsCandidateAssetUsingCanonicalListing() {
        AssetListing listing = new AssetListing(Market.US, "SOXL", "Direxion Daily Semiconductor Bull 3x", ListingStatus.ACTIVE);

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(assetListingService.ensureActiveListingForTrade(Market.US, "soxl"))
                .thenReturn(listing);
        when(portfolioCandidateAssetRepository
                .existsByPortfolio_IdAndMarketAndTicker(10L, Market.US, "SOXL"))
                .thenReturn(false);
        when(portfolioCandidateAssetRepository.save(any(PortfolioCandidateAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioCandidateAsset result = service.create(1L, 10L, Market.US, "soxl", " 디렉시온 반도체 불3배 ");

        assertThat(result.getMarket()).isEqualTo(Market.US);
        assertThat(result.getTicker()).isEqualTo("SOXL");
        assertThat(result.getDisplayName()).isEqualTo("디렉시온 반도체 불3배");
        assertThat(result.getInvestmentTrack()).isEqualTo(InvestmentTrack.TRACK_A);
        assertThat(result.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 12, 0, 0));
    }

    @Test
    void rejectsDuplicateCandidateAssetForSamePortfolio() {
        AssetListing listing = new AssetListing(Market.US, "SOXL", "Direxion Daily Semiconductor Bull 3x", ListingStatus.ACTIVE);

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(assetListingService.ensureActiveListingForTrade(Market.US, "SOXL"))
                .thenReturn(listing);
        when(portfolioCandidateAssetRepository
                .existsByPortfolio_IdAndMarketAndTicker(10L, Market.US, "SOXL"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, 10L, Market.US, "SOXL", "디렉시온 반도체 불3배"))
                .isInstanceOf(PortfolioCandidateAssetAlreadyExistsException.class);

        verify(portfolioCandidateAssetRepository, never()).save(any(PortfolioCandidateAsset.class));
    }

    @Test
    void rejectsInvalidTickerNotFoundInAssetListingSearch() {
        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(assetListingService.ensureActiveListingForTrade(Market.US, "ZZZZ"))
                .thenThrow(new IllegalArgumentException("거래 가능한 상장 정보를 찾을 수 없습니다: US / ZZZZ"));

        assertThatThrownBy(() -> service.create(1L, 10L, Market.US, "ZZZZ", "존재하지 않는 종목"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(portfolioCandidateAssetRepository, never())
                .existsByPortfolio_IdAndMarketAndTicker(any(), any(), any());
        verify(portfolioCandidateAssetRepository, never()).save(any(PortfolioCandidateAsset.class));
    }

    @Test
    void rejectsBlankDisplayName() {
        assertThatThrownBy(() -> service.create(1L, 10L, Market.US, "SOXL", "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(portfolioRepository, never()).findByMember_IdAndId(any(), any());
    }

    @Test
    void getsAllCandidateAssetsForPortfolio() {
        PortfolioCandidateAsset candidate = new PortfolioCandidateAsset(
                portfolio,
                Market.US,
                "SOXL",
                "디렉시온 반도체 불3배",
                InvestmentTrack.TRACK_A,
                LocalDateTime.of(2026, 9, 1, 0, 0)
        );

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(portfolioCandidateAssetRepository.findAllByPortfolio_Id(10L))
                .thenReturn(List.of(candidate));

        List<PortfolioCandidateAsset> results = service.getCandidateAssets(1L, 10L);

        assertThat(results).containsExactly(candidate);
    }

    @Test
    void deletesExistingCandidateAsset() {
        PortfolioCandidateAsset candidate = new PortfolioCandidateAsset(
                portfolio,
                Market.US,
                "SOXL",
                "디렉시온 반도체 불3배",
                InvestmentTrack.TRACK_A,
                LocalDateTime.of(2026, 9, 1, 0, 0)
        );

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(portfolioCandidateAssetRepository
                .findByPortfolio_IdAndMarketAndTicker(10L, Market.US, "SOXL"))
                .thenReturn(Optional.of(candidate));

        service.delete(1L, 10L, Market.US, "SOXL");

        verify(portfolioCandidateAssetRepository).delete(candidate);
    }

    @Test
    void throwsNotFoundWhenDeletingMissingCandidateAsset() {
        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(portfolioCandidateAssetRepository
                .findByPortfolio_IdAndMarketAndTicker(10L, Market.US, "SOXL"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 10L, Market.US, "SOXL"))
                .isInstanceOf(PortfolioCandidateAssetNotFoundException.class);
    }
}
