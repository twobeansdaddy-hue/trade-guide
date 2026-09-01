package com.tradeguide.repository.strategy;

import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetProfileRepository extends JpaRepository<AssetProfile, Long> {
    @Query("""
            select assetProfile
            from AssetProfile assetProfile
            where assetProfile.listing.market = :market
              and assetProfile.listing.ticker = :ticker
            """)
    Optional<AssetProfile> findByMarketAndTicker(
            @Param("market") Market market,
            @Param("ticker") String ticker
    );

    @Query("""
            select case when count(assetProfile) > 0 then true else false end
            from AssetProfile assetProfile
            where assetProfile.listing.market = :market
              and assetProfile.listing.ticker = :ticker
            """)
    boolean existsByMarketAndTicker(
            @Param("market") Market market,
            @Param("ticker") String ticker
    );

    List<AssetProfile> findAllByInvestmentTrack(
            InvestmentTrack investmentTrack
    );
}
