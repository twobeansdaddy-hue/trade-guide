package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyTrend;
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
                            toHoldingDecision(strategyDecision)
                    );
                })
                .toList();
    }

    private StrategyDecision toHoldingDecision(StrategyDecision marketDecision) {
        if (marketDecision.getTrend() == StrategyTrend.ABOVE_LONG_AVERAGE) {
            return new StrategyDecision(
                    StrategyAction.HOLD,
                    marketDecision.getReferencePrice(),
                    "상승 추세가 유지되고 있어 현재 보유 수량을 유지합니다. "
                            + marketDecision.getReason(),
                    marketDecision.getMetadata(),
                    marketDecision.getTrend(),
                    marketDecision.getSignalEvent()
            );
        }

        return new StrategyDecision(
                StrategyAction.SELL,
                marketDecision.getReferencePrice(),
                "하락 추세가 유지되고 있어 현재 보유 수량의 매도를 검토합니다. "
                        + marketDecision.getReason(),
                marketDecision.getMetadata(),
                marketDecision.getTrend(),
                marketDecision.getSignalEvent()
        );
    }
}
