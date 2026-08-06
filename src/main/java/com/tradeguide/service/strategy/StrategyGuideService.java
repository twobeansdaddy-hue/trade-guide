package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.service.market.MarketHistoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StrategyGuideService {

    private static final int WEEKLY_CANDLE_OUTPUT_SIZE = 100;

    private final AssetProfileRepository assetProfileRepository;
    private final MarketHistoryService marketHistoryService;
    private final StrategySelector strategySelector;

    public StrategyGuideService(
            AssetProfileRepository assetProfileRepository,
            MarketHistoryService marketHistoryService,
            StrategySelector strategySelector
    ) {
        this.assetProfileRepository = assetProfileRepository;
        this.marketHistoryService = marketHistoryService;
        this.strategySelector = strategySelector;
    }

    public StrategyDecision getStrategyDecision(
            Market market,
            String ticker
    ) {

        AssetProfile assetProfile = assetProfileRepository
                .findByMarketAndTicker(market, ticker)
                .orElseThrow(() -> new AssetProfileNotFoundException(market, ticker));

        List<MarketCandle> candles = marketHistoryService.getCandles(
                assetProfile.getMarket(),
                assetProfile.getTicker(),
                CandleInterval.WEEKLY,
                WEEKLY_CANDLE_OUTPUT_SIZE
        );

        TradingStrategy strategy = strategySelector.select(
                assetProfile.getInvestmentTrack()
        );

        return strategy.decide(assetProfile, candles);
    }
}
