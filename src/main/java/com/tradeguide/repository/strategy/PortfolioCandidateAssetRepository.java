package com.tradeguide.repository.strategy;

import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioCandidateAsset;
import com.tradeguide.domain.trade.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioCandidateAssetRepository extends JpaRepository<PortfolioCandidateAsset, Long> {

    List<PortfolioCandidateAsset> findAllByPortfolio_Id(Long portfolioId);

    List<PortfolioCandidateAsset> findAllByPortfolio_IdAndInvestmentTrack(
            Long portfolioId,
            InvestmentTrack investmentTrack
    );

    Optional<PortfolioCandidateAsset> findByPortfolio_IdAndMarketAndTicker(
            Long portfolioId,
            Market market,
            String ticker
    );

    boolean existsByPortfolio_IdAndMarketAndTicker(
            Long portfolioId,
            Market market,
            String ticker
    );
}
