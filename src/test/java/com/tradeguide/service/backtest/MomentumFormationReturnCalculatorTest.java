package com.tradeguide.service.backtest;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MomentumFormationReturnCalculatorTest {

    private final MomentumFormationReturnCalculator calculator = new MomentumFormationReturnCalculator();

    @Test
    void calculatesReturnFromFiftyTwoWeeksAgoExcludingLastFourWeeks() {
        // Given: 53개 캔들(index 0~52). 52주 전(index 0) 종가 100, 4주 전(index 48) 종가 150.
        // index 49~52(최근 4주)는 형성기간 계산에서 제외되므로 임의의 값(예: 급등)을 넣어도 결과에 영향이 없어야 한다.
        List<MarketCandle> candles = flatThenOverride(53, "100", 48, "150");
        overrideClose(candles, 52, "9999");

        // When
        Optional<BigDecimal> result = calculator.calculate(candles);

        // Then: (150 - 100) / 100 * 100 = 50%
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("50");
    }

    @Test
    void returnsEmptyWhenHistoryIsShorterThanFiftyTwoWeeks() {
        // Given: 52개 캔들만 있어 52주 전 종가를 참조할 수 없다.
        List<MarketCandle> candles = flatCandles(52, "100");

        // When
        Optional<BigDecimal> result = calculator.calculate(candles);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void throwsWhenCandlesAreNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> calculator.calculate(null));
    }

    @Test
    void throwsWhenBasePriceIsNotPositive() {
        List<MarketCandle> candles = flatThenOverride(53, "100", 0, "0");

        assertThatIllegalArgumentException().isThrownBy(() -> calculator.calculate(candles));
    }

    private List<MarketCandle> flatCandles(int count, String close) {
        List<MarketCandle> candles = new ArrayList<>();
        LocalDate start = LocalDate.of(2020, 1, 3);
        for (int i = 0; i < count; i++) {
            candles.add(candle(start.plusWeeks(i), close));
        }
        return candles;
    }

    private List<MarketCandle> flatThenOverride(int count, String baseClose, int overrideIndex, String overrideClose) {
        List<MarketCandle> candles = flatCandles(count, baseClose);
        overrideClose(candles, overrideIndex, overrideClose);
        return candles;
    }

    private void overrideClose(List<MarketCandle> candles, int index, String close) {
        MarketCandle original = candles.get(index);
        candles.set(index, candle(original.getTradingDate(), close));
    }

    private MarketCandle candle(LocalDate tradingDate, String close) {
        BigDecimal price = new BigDecimal(close);
        return new MarketCandle(Market.US, "AAA", tradingDate, price, price, price, price, 1_000L);
    }
}
