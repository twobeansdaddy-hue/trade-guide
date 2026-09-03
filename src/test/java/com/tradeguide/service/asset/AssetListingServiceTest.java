package com.tradeguide.service.asset;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.AssetSearchResult;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.asset.AssetListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetListingServiceTest {

    @Mock
    private AssetListingRepository assetListingRepository;

    @Mock
    private AssetSearchProvider assetSearchProvider;

    @Mock
    private AssetSearchCache assetSearchCache;

    @InjectMocks
    private AssetListingService assetListingService;

    @Test
    void returnsNoResultForBlankQuery() {
        assertThat(assetListingService.searchActiveListings(Market.US, "  ")).isEmpty();

        verifyNoInteractions(assetListingRepository);
    }

    @Test
    void searchesOnlyActiveListingsInRequestedMarket() {
        com.tradeguide.domain.asset.AssetListing soxl = new com.tradeguide.domain.asset.AssetListing(Market.US, "SOXL", "Direxion Daily Semiconductor Bull 3X Shares", ListingStatus.ACTIVE);
        when(assetListingRepository.searchActiveListings(Market.US, ListingStatus.ACTIVE, "so"))
                .thenReturn(List.of(soxl));
        when(assetSearchCache.getOrLoad(
                org.mockito.ArgumentMatchers.eq(Market.US),
                org.mockito.ArgumentMatchers.eq("so"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(new AssetSearchResult(Market.US, "SOFI", "SoFi Technologies")));

        assertThat(assetListingService.searchActiveListings(Market.US, " so "))
                .extracting(AssetSearchResult::ticker)
                .containsExactly("SOXL", "SOFI");
    }

    @Test
    void reusesExistingActiveListingForTrade() {
        AssetListing existing = new AssetListing(Market.US, "AAPL", "Apple Inc.", ListingStatus.ACTIVE);
        when(assetListingRepository.findByMarketAndTicker(Market.US, "AAPL"))
                .thenReturn(Optional.of(existing));

        AssetListing result = assetListingService.ensureActiveListingForTrade(Market.US, " aapl ");

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(assetSearchProvider, assetSearchCache);
    }

    @Test
    void rejectsTradeForInactiveListing() {
        AssetListing inactive = new AssetListing(Market.US, "AAPL", "Apple Inc.", ListingStatus.INACTIVE);
        when(assetListingRepository.findByMarketAndTicker(Market.US, "AAPL"))
                .thenReturn(Optional.of(inactive));

        assertThatThrownBy(() ->
                assetListingService.ensureActiveListingForTrade(Market.US, "AAPL")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비활성 상장 종목");

        verify(assetListingRepository, never()).save(any(AssetListing.class));
    }

    @Test
    void createsActiveListingFromExactExternalSearchMatch() {
        when(assetListingRepository.findByMarketAndTicker(Market.US, "SOFI"))
                .thenReturn(Optional.empty());
        when(assetSearchCache.getOrLoad(
                org.mockito.ArgumentMatchers.eq(Market.US),
                org.mockito.ArgumentMatchers.eq("SOFI"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(
                new AssetSearchResult(Market.US, "SOFI", "SoFi Technologies"),
                new AssetSearchResult(Market.US, "SOFIW", "SoFi Warrant")
        ));
        when(assetListingRepository.save(any(AssetListing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssetListing result = assetListingService.ensureActiveListingForTrade(Market.US, "sofi");

        assertThat(result.getMarket()).isEqualTo(Market.US);
        assertThat(result.getTicker()).isEqualTo("SOFI");
        assertThat(result.getDisplayName()).isEqualTo("SoFi Technologies");
        assertThat(result.getListingStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void failsWhenNoExactExternalSearchMatchExists() {
        when(assetListingRepository.findByMarketAndTicker(Market.US, "ZZZZ"))
                .thenReturn(Optional.empty());
        when(assetSearchCache.getOrLoad(
                org.mockito.ArgumentMatchers.eq(Market.US),
                org.mockito.ArgumentMatchers.eq("ZZZZ"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(new AssetSearchResult(Market.US, "ZZZZW", "Not An Exact Match")));

        assertThatThrownBy(() ->
                assetListingService.ensureActiveListingForTrade(Market.US, "ZZZZ")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("거래 가능한 상장 정보를 찾을 수 없습니다");

        verify(assetListingRepository, never()).save(any(AssetListing.class));
    }

}
