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

    private MarketCandle candle(String close) {
        return new MarketCandle(Market.US, "SOXL", LocalDate.of(2026, 9, 11),
                new BigDecimal("98.00"), new BigDecimal("102.00"), new BigDecimal("97.00"),
                new BigDecimal(close), 1000);
    }
}
