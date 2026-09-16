package com.tradeguide.domain.market;

import com.tradeguide.domain.trade.Currency;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketPriceTest {

    @Test
    void acceptsCurrencyMatchingTheMarket() {
        MarketPrice price = new MarketPrice(Market.KR, "005930", new BigDecimal("70000"), Currency.KRW, Instant.now());

        assertThat(price.getCurrency()).isEqualTo(Currency.KRW);
    }

    @Test
    void rejectsCurrencyMismatchedWithTheMarket() {
        assertThatThrownBy(() ->
                new MarketPrice(Market.US, "AAPL", new BigDecimal("210.50"), Currency.KRW, Instant.now())
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
