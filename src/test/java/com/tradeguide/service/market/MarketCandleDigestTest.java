package com.tradeguide.service.market;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketCandleDigestTest {

    private final MarketCandleDigest digest = new MarketCandleDigest();

    @Test
    void identicalCompletedCandlesHaveStableHashAndPriceChangesAlterIt() {
        MarketCandle original = candle("100.00");
        String first = digest.sha256(MarketDataProvider.TWELVE_DATA, CandleInterval.WEEKLY,
                List.of(original));

        assertThat(first).hasSize(64).isEqualTo(digest.sha256(
                MarketDataProvider.TWELVE_DATA, CandleInterval.WEEKLY, List.of(candle("100.00"))));
        assertThat(first).isNotEqualTo(digest.sha256(
                MarketDataProvider.TWELVE_DATA, CandleInterval.WEEKLY, List.of(candle("101.00"))));
        assertThat(first).isNotEqualTo(digest.sha256(
                MarketDataProvider.YAHOO_FINANCE, CandleInterval.WEEKLY, List.of(original)));
    }

    @Test
    void sameValuesWithDifferentScaleHaveSameHash() {
        String twoDecimals = hash(candle("98.00", "102.00", "97.00", "100.00"));

        assertThat(hash(candle("98", "102.0", "97.000", "100"))).isEqualTo(twoDecimals);
        assertThat(hash(candle("98.0", "1.02E+2", "97", "1E+2"))).isEqualTo(twoDecimals);
        assertThat(hash(candle("0.00", "102", "0", "100"))).isEqualTo(hash(candle("0", "102", "0.0", "100")));
    }

    @Test
    void normalizationStillSeparatesDifferentValues() {
        assertThat(hash(candle("98", "102", "97", "100.5")))
                .isNotEqualTo(hash(candle("98", "102", "97", "100.05")));
        assertThat(hash(candle("98", "102", "97", "100")))
                .isNotEqualTo(hash(candle("98", "102", "97", "1000")));
    }

    private String hash(MarketCandle candle) {
        return digest.sha256(MarketDataProvider.TWELVE_DATA, CandleInterval.WEEKLY, List.of(candle));
    }

    private MarketCandle candle(String close) {
        return candle("98.00", "102.00", "97.00", close);
    }

    private MarketCandle candle(String open, String high, String low, String close) {
        return new MarketCandle(Market.US, "SOXL", LocalDate.of(2026, 9, 11),
                new BigDecimal(open), new BigDecimal(high), new BigDecimal(low),
                new BigDecimal(close), 1000);
    }
}
