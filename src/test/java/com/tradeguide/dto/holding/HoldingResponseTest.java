package com.tradeguide.dto.holding;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HoldingResponseTest {

    @Test
    void roundsAveragePurchasePriceToTwoDecimalPlaces() {
        Holding holding = new Holding(
                Market.US,
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("100.0150000000")
        );

        HoldingResponse response = HoldingResponse.from(holding);

        assertThat(response.getAveragePurchasePrice())
                .isEqualTo(new BigDecimal("100.02"));
    }
}