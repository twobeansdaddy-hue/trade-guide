package com.tradeguide.service.indicator;

import com.tradeguide.domain.market.MarketCandle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class SimpleMovingAverageCalculator {

    public BigDecimal calculate(
            List<MarketCandle> candles,
            int period
    ) {
        if (period < 1) {
            throw new IllegalArgumentException(
                    "이동평균 기간은 1 이상이어야 합니다."
            );
        }

        if (candles.size() < period) {
            throw new IllegalArgumentException(
                    "이동평균을 계산하기 위한 일봉 데이터가 부족합니다."
            );
        }

        BigDecimal totalClosePrice = candles.stream()
                .skip(candles.size() - period)
                .map(MarketCandle::getClose)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalClosePrice.divide(
                BigDecimal.valueOf(period),
                4,
                RoundingMode.HALF_UP
        );
    }
}
