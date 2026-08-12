package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyTrend;
import com.tradeguide.domain.strategy.StrategySignal;
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
                    StrategySignal signal = strategyGuideService.getStrategySignal(
                            holding.getMarket(),
                            holding.getTicker()
                    );

                    return new AssetStrategyGuide(
                            holding.getMarket(),
                            holding.getTicker(),
                            toHoldingDecision(signal)
                    );
                })
                .toList();
    }

    private StrategyDecision toHoldingDecision(StrategySignal signal) {
        if (signal.getTrend() == StrategyTrend.ABOVE_LONG_AVERAGE) {
            return new StrategyDecision(
                    StrategyAction.HOLD,
                    "상승 추세가 유지되고 있어 현재 보유 수량을 유지합니다.",
                    signal
            );
        }

        return new StrategyDecision(
                StrategyAction.SELL,
                "하락 추세가 유지되고 있어 현재 보유 수량의 매도를 검토합니다.",
                signal
        );
    }
}
