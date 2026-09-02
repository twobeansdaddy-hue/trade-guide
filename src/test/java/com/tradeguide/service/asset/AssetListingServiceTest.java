package com.tradeguide.service.asset;

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

import static org.assertj.core.api.Assertions.assertThat;
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
}
