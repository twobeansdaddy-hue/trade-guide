package com.tradeguide.repository.asset;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.trade.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetListingRepository extends JpaRepository<AssetListing, Long> {

    Optional<AssetListing> findByMarketAndTicker(Market market, String ticker);
}
