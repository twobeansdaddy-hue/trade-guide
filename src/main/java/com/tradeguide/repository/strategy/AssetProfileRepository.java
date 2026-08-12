package com.tradeguide.repository.strategy;

import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.trade.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetProfileRepository extends JpaRepository<AssetProfile, Long> {
    Optional<AssetProfile> findByMarketAndTicker(
            Market market,
            String ticker
    );

    boolean existsByMarketAndTicker(
            Market market,
            String ticker
    );
}
