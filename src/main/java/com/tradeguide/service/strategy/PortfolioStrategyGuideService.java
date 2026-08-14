package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.UnavailableAsset;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public StrategyGuideBatch getPortfolioStrategyGuides(
            Long memberId,
            Long portfolioId
    ) {
        List<AssetStrategyGuide> guides = new ArrayList<>();
        List<UnavailableAsset> unavailableAssets = new ArrayList<>();

        List<Holding> holdings = holdingService.getHoldings(
                memberId,
                portfolioId
        );

        for (int index = 0; index < holdings.size(); index++) {
            Holding holding = holdings.get(index);

            try {
                StrategySignal signal = strategyGuideService.getStrategySignal(
                        holding.getMarket(),
                        holding.getTicker()
                );

                guides.add(new AssetStrategyGuide(
                        holding.getMarket(),
                        holding.getTicker(),
                        strategyDecisionMaker.decideForHolding(signal)
                ));
            } catch (MarketDataRateLimitExceededException exception) {
                unavailableAssets.add(new UnavailableAsset(
                        holding.getMarket(),
                        holding.getTicker(),
                        exception.getMessage()
                ));

                addRateLimitedAssets(
                        holdings,
                        index + 1,
                        unavailableAssets
                );

                break;
            } catch (MarketDataUnavailableException exception) {
                unavailableAssets.add(new UnavailableAsset(
                        holding.getMarket(),
                        holding.getTicker(),
                        exception.getMessage()
                ));
            }
        }

        return new StrategyGuideBatch(guides, unavailableAssets);
    }

    private void addRateLimitedAssets(
            List<Holding> holdings,
            int startIndex,
            List<UnavailableAsset> unavailableAssets
    ) {
        for (int index = startIndex; index < holdings.size(); index++) {
            Holding holding = holdings.get(index);

            unavailableAssets.add(new UnavailableAsset(
                    holding.getMarket(),
                    holding.getTicker(),
                    "시장 데이터 요청 제한으로 조회하지 못했습니다."
            ));
        }
    }
}
