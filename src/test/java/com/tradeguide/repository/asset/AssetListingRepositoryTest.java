package com.tradeguide.repository.asset;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AssetListingRepositoryTest {

    @Autowired
    private AssetListingRepository assetListingRepository;

    @Test
    void findsKoreanListingByMarketAndTicker() {
        assetListingRepository.save(new AssetListing(
                Market.KR,
                "005930",
                "삼성전자",
                ListingStatus.ACTIVE
        ));

        AssetListing listing = assetListingRepository
                .findByMarketAndTicker(Market.KR, "005930")
                .orElseThrow();

        assertThat(listing.getDisplayName()).isEqualTo("삼성전자");
        assertThat(listing.getListingStatus()).isEqualTo(ListingStatus.ACTIVE);
    }
}
