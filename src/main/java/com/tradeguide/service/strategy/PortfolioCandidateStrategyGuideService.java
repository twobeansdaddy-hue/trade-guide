package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioCandidateStrategyGuideService {

    private final HoldingService holdingService;
    private final AssetProfileRepository assetProfileRepository;
    private final StrategyGuideService strategyGuideService;
    private final StrategyDecisionMaker strategyDecisionMaker;

    public PortfolioCandidateStrategyGuideService(
            HoldingService holdingService,
            AssetProfileRepository assetProfileRepository,
            StrategyGuideService strategyGuideService,
            StrategyDecisionMaker strategyDecisionMaker
    ) {
        this.holdingService = holdingService;
        this.assetProfileRepository = assetProfileRepository;
        this.strategyGuideService = strategyGuideService;
        this.strategyDecisionMaker = strategyDecisionMaker;
    }

    public List<AssetStrategyGuide> getCandidateStrategyGuides(
            Long memberId,
            Long portfolioId
    ) {
        List<Holding> holdings = holdingService.getHoldings(memberId, portfolioId);

        return assetProfileRepository
                .findAllByInvestmentTrack(InvestmentTrack.TRACK_A)
                .stream()
                .filter(assetProfile -> holdings.stream()
                        .noneMatch(holding ->
                                holding.getMarket() == assetProfile.getMarket()
                                        && holding.getTicker()
                                        .equals(assetProfile.getTicker())
                        )
                )
                .map(assetProfile -> {
                    StrategySignal signal = strategyGuideService.getStrategySignal(
                            assetProfile.getMarket(),
                            assetProfile.getTicker()
                    );

                    return new AssetStrategyGuide(
                            assetProfile.getMarket(),
                            assetProfile.getTicker(),
                            strategyDecisionMaker.decideForCandidate(signal)
                    );
                })
                .toList();
    }
}
