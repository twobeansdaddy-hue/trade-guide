package com.tradeguide.service.strategy.tracka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.*;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.service.indicator.SimpleMovingAverageCalculator;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyMaCrossoverGoldenTest {

    private static final String CANDLE_FIXTURE =
            "fixtures/market/soxl-weekly-twelvedata-2021-2026.csv";
    private static final String EXPECTATION_FIXTURE =
            "fixtures/market/soxl-weekly-ma10-ma40-expectations.json";

    private final WeeklyMaCrossoverStrategy strategy =
            new WeeklyMaCrossoverStrategy(new SimpleMovingAverageCalculator());
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void reproducesExpectedTrackASignalsFromTwelveDataSnapshot()
            throws IOException {
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );
        List<MarketCandle> candles = readCandles();

        for (GoldenExpectation expectation : readExpectations()) {
            int candleIndex = findCandleIndex(candles, expectation.date());

            assertThat(candleIndex)
                    .as("fixture contains %s", expectation.date())
                    .isGreaterThanOrEqualTo(0);

            StrategySignal signal = strategy.decide(
                    assetProfile,
                    candles.subList(0, candleIndex + 1)
            );

            assertThat(signal.getTrend()).isEqualTo(expectation.trend());
            assertThat(signal.getSignalEvent())
                    .isEqualTo(expectation.signalEvent());
        }
    }

    private List<MarketCandle> readCandles() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource(CANDLE_FIXTURE),
                StandardCharsets.UTF_8
        ))) {
            return reader.lines()
                    .skip(1)
                    .map(this::toMarketCandle)
                    .toList();
        }
    }

    private List<GoldenExpectation> readExpectations() throws IOException {
        return objectMapper.readValue(
                resource(EXPECTATION_FIXTURE),
                new TypeReference<>() {
                }
        );
    }

    private InputStream resource(String name) {
        return Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream(name),
                () -> "테스트 픽스처를 찾을 수 없습니다: " + name
        );
    }

    private MarketCandle toMarketCandle(String line) {
        String[] columns = line.split(";", -1);

        return new MarketCandle(
                Market.US,
                "SOXL",
                LocalDate.parse(columns[0]),
                new BigDecimal(columns[1]),
                new BigDecimal(columns[2]),
                new BigDecimal(columns[3]),
                new BigDecimal(columns[4]),
                Long.parseLong(columns[5])
        );
    }

    private int findCandleIndex(
            List<MarketCandle> candles,
            LocalDate tradingDate
    ) {
        for (int index = 0; index < candles.size(); index++) {
            if (candles.get(index).getTradingDate().equals(tradingDate)) {
                return index;
            }
        }

        return -1;
    }

    private record GoldenExpectation(
            LocalDate date,
            StrategyTrend trend,
            StrategySignalEvent signalEvent
    ) {
    }
}
