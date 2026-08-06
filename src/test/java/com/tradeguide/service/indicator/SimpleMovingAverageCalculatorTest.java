package com.tradeguide.service.indicator;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SimpleMovingAverageCalculatorTest {

    private final SimpleMovingAverageCalculator calculator =
            new SimpleMovingAverageCalculator();

    @Test
    void calculatesAverageOfLatestCandles() {
        // Given
        List<MarketCandle> candles = List.of(
                candle("2026-08-01", "90"),
                candle("2026-08-02", "100"),
                candle("2026-08-03", "110"),
                candle("2026-08-04", "120")
        );

        // When
        BigDecimal result = calculator.calculate(candles, 3);

        // Then
        assertThat(result).isEqualByComparingTo("110.0000");
        assertThat(result.scale()).isEqualTo(4);
    }

    @Test
    void throwsExceptionWhenCandlesAreInsufficient() {
        List<MarketCandle> candles = List.of(
                candle("2026-08-01", "100"),
                candle("2026-08-02", "110")
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(candles, 3))
                .withMessage(
                        "이동평균을 계산하기 위한 캔들 데이터가 부족합니다."
                );
    }

    @Test
    void throwsExceptionWhenPeriodIsLessThanOne() {
        List<MarketCandle> candles = List.of(
                candle("2026-08-01", "100")
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(candles, 0))
                .withMessage("이동평균 기간은 1 이상이어야 합니다.");
    }

    private MarketCandle candle(
            String tradingDate,
            String close
    ) {
        BigDecimal price = new BigDecimal(close);

        return new MarketCandle(
                Market.US,
                "AAPL",
                LocalDate.parse(tradingDate),
                price,
                price,
                price,
                price,
                1_000L
        );
    }
}