package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CompletedWeeklyCandleFilter {

    private final WeeklyCandleSchedule weeklyCandleSchedule;

    public CompletedWeeklyCandleFilter(WeeklyCandleSchedule weeklyCandleSchedule) {
        this.weeklyCandleSchedule = weeklyCandleSchedule;
    }

    public List<MarketCandle> filter(List<MarketCandle> candles) {
        LocalDate firstIncompleteCandleStart = weeklyCandleSchedule.getFirstIncompleteCandleStart();

        return candles.stream()
                .filter(candle -> candle.getTradingDate().isBefore(firstIncompleteCandleStart))
                .toList();

    }
}
