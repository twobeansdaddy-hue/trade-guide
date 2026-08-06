package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.StrategyDecision;

import java.util.List;

public interface TradingStrategy {

    boolean supports(InvestmentTrack investmentTrack);

    StrategyDecision decide(
            AssetProfile assetProfile,
            List<MarketCandle> candles
    );
}
