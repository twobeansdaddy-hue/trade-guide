package com.tradeguide.service.market;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;

import java.util.List;

public interface MarketHistoryProvider {

    List<MarketCandle> getCandles(
            Market market,
            String ticker,
            CandleInterval interval,
            int outputSize
    );
}