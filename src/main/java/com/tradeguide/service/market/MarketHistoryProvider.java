package com.tradeguide.service.market;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.trade.Market;

import java.util.List;

public interface MarketHistoryProvider {

    MarketDataProvider getProvider();

    List<MarketCandle> getCandles(
            Market market,
            String ticker,
            CandleInterval interval,
            int outputSize
    );

    default ObservedMarketCandles getObservedCandles(
            Market market, String ticker, CandleInterval interval, int outputSize
    ) {
        return new ObservedMarketCandles(
                getCandles(market, ticker, interval, outputSize), java.util.Optional.empty());
    }
}
