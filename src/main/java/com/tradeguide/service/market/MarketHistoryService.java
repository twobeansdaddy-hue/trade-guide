package com.tradeguide.service.market;


import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.trade.Market;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarketHistoryService {

    private final MarketHistoryProvider marketHistoryProvider;
    private final MarketHistoryProviderRegistry marketHistoryProviderRegistry;

    public MarketHistoryService(
            MarketHistoryProvider marketHistoryProvider,
            MarketHistoryProviderRegistry marketHistoryProviderRegistry
    ) {
        this.marketHistoryProvider = marketHistoryProvider;
        this.marketHistoryProviderRegistry = marketHistoryProviderRegistry;
    }

    public List<MarketCandle> getCandles(
            Market market,
            String ticker,
            CandleInterval interval,
            int outputSize
    ) {
        return marketHistoryProvider.getCandles(
                market,
                ticker,
                interval,
                outputSize
        );
    }

    /** 포트폴리오가 선택한 캔들 제공자를 사용한다. */
    public List<MarketCandle> getCandles(
            MarketDataProvider providerType,
            Long portfolioId,
            Market market,
            String ticker,
            CandleInterval interval,
            int outputSize
    ) {
        return marketHistoryProviderRegistry
                .resolve(providerType, portfolioId)
                .getCandles(market, ticker, interval, outputSize);
    }

    public ObservedMarketCandles getObservedCandles(
            MarketDataProvider providerType, Long portfolioId, Market market,
            String ticker, CandleInterval interval, int outputSize
    ) {
        return marketHistoryProviderRegistry.resolve(providerType, portfolioId)
                .getObservedCandles(market, ticker, interval, outputSize);
    }
}
