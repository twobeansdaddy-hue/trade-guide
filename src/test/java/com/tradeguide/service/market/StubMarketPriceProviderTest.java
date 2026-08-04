package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubMarketPriceProviderTest {

    private final StubMarketPriceProvider provider =
            new StubMarketPriceProvider();

    @Test
    void returnsCurrentPriceForKnownTicker() {
        // when
        MarketPrice marketPrice = provider.getCurrentPrice(Market.US, "aapl");

        // then
        assertThat(marketPrice.getMarket()).isEqualTo(Market.US);
        assertThat(marketPrice.getTicker()).isEqualTo("AAPL");
        assertThat(marketPrice.getCurrentPrice())
                .isEqualByComparingTo("210.50");
        assertThat(marketPrice.getCapturedAt()).isNotNull();
    }

    @Test
    void throwsExceptionForUnknownTicker() {
        assertThatThrownBy(() ->
                provider.getCurrentPrice(Market.US, "UNKNOWN")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재가를 찾을 수 없습니다.");
    }
}