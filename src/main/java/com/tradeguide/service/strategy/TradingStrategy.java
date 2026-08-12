package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategySignal;

import java.util.List;

public interface TradingStrategy {

    boolean supports(InvestmentTrack investmentTrack);

    StrategySignal decide(
            AssetProfile assetProfile,
            List<MarketCandle> candles
    );
}
