package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.service.market.CompletedWeeklyCandleFilter;
import com.tradeguide.service.market.MarketHistoryService;
import com.tradeguide.service.market.WeeklyCandleFreshnessValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StrategyGuideService {

    // 완료 처리 중인 이번 주 봉을 제외해도, 40주 이동평균 계산에 필요한 이력을 확보한다.
    private static final int WEEKLY_CANDLE_OUTPUT_SIZE = 101;

    private final AssetProfileRepository assetProfileRepository;
    private final MarketHistoryService marketHistoryService;
    private final StrategySelector strategySelector;
    private final CompletedWeeklyCandleFilter completedWeeklyCandleFilter;
    private final WeeklyCandleFreshnessValidator weeklyCandleFreshnessValidator;

    public StrategyGuideService(
            AssetProfileRepository assetProfileRepository,
            MarketHistoryService marketHistoryService,
            StrategySelector strategySelector,
            CompletedWeeklyCandleFilter completedWeeklyCandleFilter,
            WeeklyCandleFreshnessValidator weeklyCandleFreshnessValidator
    ) {
        this.assetProfileRepository = assetProfileRepository;
        this.marketHistoryService = marketHistoryService;
        this.strategySelector = strategySelector;
        this.completedWeeklyCandleFilter = completedWeeklyCandleFilter;
        this.weeklyCandleFreshnessValidator = weeklyCandleFreshnessValidator;
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

        List<MarketCandle> completedCandles = completedWeeklyCandleFilter.filter(candles);

        weeklyCandleFreshnessValidator.validate(completedCandles);

        TradingStrategy strategy = strategySelector.select(
                assetProfile.getInvestmentTrack()
        );

        return strategy.decide(assetProfile, completedCandles);
    }
}
