package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.exception.UnsupportedInvestmentTrackException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StrategySelector {

    private final List<TradingStrategy> strategies;

    public StrategySelector(List<TradingStrategy> strategies) {
        this.strategies = strategies;
    }

    public TradingStrategy select(InvestmentTrack investmentTrack) {
        return strategies.stream()
                .filter(tradingStrategy -> tradingStrategy.supports(investmentTrack))
                .findFirst()
                .orElseThrow(() -> new UnsupportedInvestmentTrackException(
                        "지원하지 않는 투자 트랙입니다: " + investmentTrack
                ));
    }
}
