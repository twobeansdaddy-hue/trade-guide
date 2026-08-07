package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioStrategyGuideService {

    private final HoldingService holdingService;
    private final StrategyGuideService strategyGuideService;

    public PortfolioStrategyGuideService(
            HoldingService holdingService,
            StrategyGuideService strategyGuideService
    ) {
        this.holdingService = holdingService;
        this.strategyGuideService = strategyGuideService;
    }

    public List<AssetStrategyGuide> getPortfolioStrategyGuides(
            Long memberId,
            Long portfolioId
    ) {

        return holdingService.getHoldings(memberId, portfolioId)
                .stream()
                .map(holding -> {
                    StrategyDecision strategyDecision = strategyGuideService.getStrategyDecision(
                            holding.getMarket(),
                            holding.getTicker()
                    );

                    return new AssetStrategyGuide(
                            holding.getMarket(),
                            holding.getTicker(),
                            strategyDecision
                    );
                })
                .toList();
    }
}
