package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioStrategyGuideService {

    private final HoldingService holdingService;
    private final StrategyGuideService strategyGuideService;
    private final StrategyDecisionMaker strategyDecisionMaker;

    public PortfolioStrategyGuideService(
            HoldingService holdingService,
            StrategyGuideService strategyGuideService,
            StrategyDecisionMaker strategyDecisionMaker
    ) {
        this.holdingService = holdingService;
        this.strategyGuideService = strategyGuideService;
        this.strategyDecisionMaker = strategyDecisionMaker;
    }

    public List<AssetStrategyGuide> getPortfolioStrategyGuides(
            Long memberId,
            Long portfolioId
    ) {

        return holdingService.getHoldings(memberId, portfolioId)
                .stream()
                .map(holding -> {
                    StrategySignal signal = strategyGuideService.getStrategySignal(
                            holding.getMarket(),
                            holding.getTicker()
                    );

                    return new AssetStrategyGuide(
                            holding.getMarket(),
                            holding.getTicker(),
                            strategyDecisionMaker.decideForHolding(signal)
                    );
                })
                .toList();
    }
}
