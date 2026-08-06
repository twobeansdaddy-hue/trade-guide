package com.tradeguide.controller.market;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.market.MarketCandleResponse;
import com.tradeguide.service.market.MarketHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/markets/{market}/stocks/{ticker}/candles")
public class MarketHistoryController {

    private final MarketHistoryService marketHistoryService;

    public MarketHistoryController(
            MarketHistoryService marketHistoryService
    ) {
        this.marketHistoryService = marketHistoryService;
    }

    @GetMapping("/daily")
    public List<MarketCandleResponse> getDailyCandles(
            @PathVariable Market market,
            @PathVariable String ticker,
            @RequestParam(defaultValue = "200") int outputSize
    ) {
        List<MarketCandle> candles = marketHistoryService.getDailyCandles(
                market,
                ticker,
                outputSize
        );

        return candles.stream()
                .map(MarketCandleResponse::from)
                .toList();
    }

}
