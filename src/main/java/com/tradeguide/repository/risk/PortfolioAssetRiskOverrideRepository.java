package com.tradeguide.repository.risk;

import com.tradeguide.domain.risk.PortfolioAssetRiskOverride;
import com.tradeguide.domain.trade.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioAssetRiskOverrideRepository extends JpaRepository<PortfolioAssetRiskOverride, Long> {

    List<PortfolioAssetRiskOverride> findAllByPortfolio_Id(Long portfolioId);

    Optional<PortfolioAssetRiskOverride> findByPortfolio_IdAndMarketAndTicker(
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
