package com.tradeguide.service.strategy.tracka;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
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
    void returnsBuyWhenTenWeekAverageCrossesAboveFortyWeekAverage() {
        // Given
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );
        List<MarketCandle> candles = candlesWithGoldenCross();

        // When
        StrategyDecision result = strategy.decide(assetProfile, candles);

        // Then
        assertThat(result.getAction()).isEqualTo(StrategyAction.BUY);
        assertThat(result.getReferencePrice()).isEqualByComparingTo("200");
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
    void returnsHoldWhenThereIsNoGoldenCross() {
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );
        List<MarketCandle> candles = candlesWithSameClose(41, "100");

        StrategyDecision result = strategy.decide(assetProfile, candles);

        assertThat(result.getAction()).isEqualTo(StrategyAction.HOLD);
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
    void returnsSellWhenTenWeekAverageCrossesBelowFortyWeekAverage() {
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );
        List<MarketCandle> candles = candlesWithDeathCross();

        StrategyDecision result = strategy.decide(assetProfile, candles);

        assertThat(result.getAction()).isEqualTo(StrategyAction.SELL);
        assertThat(result.getReferencePrice()).isEqualByComparingTo("50");
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