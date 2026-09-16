package com.tradeguide.domain.trade;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketTest {

    @Test
    void usMapsToUsd() {
        assertThat(Market.US.getCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void krMapsToKrw() {
        assertThat(Market.KR.getCurrency()).isEqualTo(Currency.KRW);
    }
}
