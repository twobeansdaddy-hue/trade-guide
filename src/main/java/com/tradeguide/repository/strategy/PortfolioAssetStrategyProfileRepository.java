package com.tradeguide.repository.strategy;

import com.tradeguide.domain.strategy.PortfolioAssetStrategyProfile;
import com.tradeguide.domain.trade.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioAssetStrategyProfileRepository extends JpaRepository<PortfolioAssetStrategyProfile, Long> {

    List<PortfolioAssetStrategyProfile> findAllByPortfolio_Id(Long portfolioId);

    Optional<PortfolioAssetStrategyProfile> findByPortfolio_IdAndMarketAndTicker(
            Long portfolioId,
            Market market,
            String ticker
    );

    void deleteByPortfolio_IdAndMarketAndTicker(
            Long portfolioId,
            Market market,
            String ticker
    );
}
