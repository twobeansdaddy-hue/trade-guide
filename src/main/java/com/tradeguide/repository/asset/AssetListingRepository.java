package com.tradeguide.repository.asset;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.trade.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetListingRepository extends JpaRepository<AssetListing, Long> {

    Optional<AssetListing> findByMarketAndTicker(Market market, String ticker);

    @Query("""
            select assetListing
            from AssetListing assetListing
            where assetListing.market = :market
              and assetListing.listingStatus = :listingStatus
              and (
                lower(assetListing.ticker) like lower(concat('%', :query, '%'))
                or lower(assetListing.displayName) like lower(concat('%', :query, '%'))
              )
            order by assetListing.ticker
            """)
    List<AssetListing> searchActiveListings(
            @Param("market") Market market,
            @Param("listingStatus") ListingStatus listingStatus,
            @Param("query") String query
    );
}
