package com.tradeguide.service.strategy.tracka;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.StrategySignalEvent;
import com.tradeguide.domain.strategy.StrategyTrend;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.service.indicator.SimpleMovingAverageCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WeeklyMaCrossoverStrategyTest {

    private final WeeklyMaCrossoverStrategy strategy =
            new WeeklyMaCrossoverStrategy(new SimpleMovingAverageCalculator());

    @Test
    void returnsCrossUpSignalWhenTenWeekAverageCrossesAboveFortyWeekAverage() {
        // Given
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );
        List<MarketCandle> candles = candlesWithGoldenCross();

        // When
        StrategySignal result = strategy.decide(assetProfile, candles);

        // Then
        assertThat(result.getReferencePrice()).isEqualByComparingTo("200");
        assertThat(result.getTrend()).isEqualTo(StrategyTrend.ABOVE_LONG_AVERAGE);
        assertThat(result.getSignalEvent()).isEqualTo(StrategySignalEvent.CROSS_UP);
        assertThat(result.getWeeksSinceCross()).isZero();
    }

    private List<MarketCandle> candlesWithGoldenCross() {
        return IntStream.rangeClosed(1, 41)
                .mapToObj(index -> {
                    String close = index == 41 ? "200" : "100";
                    return candle(
                            LocalDate.of(2026, 1, 2).plusWeeks(index - 1L),
                            close
                    );
                })
                .toList();
    }

    private MarketCandle candle(LocalDate tradingDate, String close) {
        BigDecimal price = new BigDecimal(close);

        return new MarketCandle(
                Market.US,
                "SOXL",
                tradingDate,
                price,
                price,
                price,
                price,
                1_000L
        );
    }

    @Test
    void returnsBelowTrendWhenBelowLongAverageWithoutNewCross() {
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );
        List<MarketCandle> candles = candlesWithSameClose(41, "100");

        StrategySignal result = strategy.decide(assetProfile, candles);

        assertThat(result.getTrend()).isEqualTo(StrategyTrend.BELOW_LONG_AVERAGE);
        assertThat(result.getSignalEvent()).isEqualTo(StrategySignalEvent.NONE);
        assertThat(result.getWeeksSinceCross()).isNull();
    }

    @Test
    void throwsExceptionWhenCandlesAreFewerThanFortyOne() {
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );
        List<MarketCandle> candles = candlesWithSameClose(40, "100");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> strategy.decide(assetProfile, candles))
                .withMessage("주봉 이동평균 교차 전략에는 최소 41개의 캔들이 필요합니다.");
    }

    @Test
    void returnsCrossDownSignalWhenTenWeekAverageCrossesBelowFortyWeekAverage() {
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );
        List<MarketCandle> candles = candlesWithDeathCross();

        StrategySignal result = strategy.decide(assetProfile, candles);

        assertThat(result.getReferencePrice()).isEqualByComparingTo("50");
        assertThat(result.getSignalEvent()).isEqualTo(StrategySignalEvent.CROSS_DOWN);
        assertThat(result.getWeeksSinceCross()).isZero();
    }

    @Test
    void throwsExceptionWhenAssetDoesNotBelongToTrackA() {
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "AAPL",
                InvestmentTrack.TRACK_B
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        strategy.decide(assetProfile, candlesWithGoldenCross()))
                .withMessage("TRACK_A 종목에서만 사용할 수 있는 전략입니다.");
    }

    private List<MarketCandle> candlesWithSameClose(
            int count,
            String close) {

        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> candle(
                        LocalDate.of(2026, 1, 2).plusWeeks(index - 1L),
                        close
                ))
                .toList();
    }

    private List<MarketCandle> candlesWithDeathCross() {
        return IntStream.rangeClosed(1, 41)
                .mapToObj(index -> {
                    String close = index == 41 ? "50" : "100";
                    return candle(
                            LocalDate.of(2026, 1, 2).plusWeeks(index - 1L),
                            close
                    );
                })
                .toList();
    }

}
