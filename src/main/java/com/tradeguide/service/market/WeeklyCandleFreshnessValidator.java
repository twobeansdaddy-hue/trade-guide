package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.exception.StaleMarketDataException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class WeeklyCandleFreshnessValidator {

    private final WeeklyCandleSchedule weeklyCandleSchedule;

    public WeeklyCandleFreshnessValidator(
            WeeklyCandleSchedule weeklyCandleSchedule
    ) {
        this.weeklyCandleSchedule = weeklyCandleSchedule;
    }

    public void validate(List<MarketCandle> completedCandles) {
        if (completedCandles.isEmpty()) {
            throw new StaleMarketDataException(
                    "완료된 주봉 데이터가 없습니다."
            );
        }

        LocalDate latestCompletedCandleDate = completedCandles.stream()
                .map(MarketCandle::getTradingDate)
                .max(LocalDate::compareTo)
                .orElseThrow();

        LocalDate expectedLatestCompletedCandleStart =
                weeklyCandleSchedule.getExpectedLatestCompletedCandleStart();

        if (latestCompletedCandleDate.isBefore(
                expectedLatestCompletedCandleStart
        )) {
            throw new StaleMarketDataException(
                    "최신 완료 주봉 데이터가 오래되었습니다."
            );
        }
    }
}