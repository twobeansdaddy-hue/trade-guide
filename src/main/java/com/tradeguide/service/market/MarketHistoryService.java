package com.tradeguide.service.market;


import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarketHistoryService {

    private final MarketHistoryProvider marketHistoryProvider;

    public MarketHistoryService(
            MarketHistoryProvider marketHistoryProvider
    ) {
        this.marketHistoryProvider = marketHistoryProvider;
    }

    public List<MarketCandle> getDailyCandles(
            Market market,
            String ticker,
            int outputSize
    ) {
        return marketHistoryProvider.getDailyCandles(
                market,
                ticker,
                outputSize
        );
    }
}
