package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerHoldingPreviewCalculatorTest {

    private final BrokerHoldingPreviewCalculator calculator = new BrokerHoldingPreviewCalculator();

    @Test
    void classifiesMatchedMismatchedAndOneSidedHoldings() {
        List<BrokerHolding> brokerHoldings = List.of(
                new BrokerHolding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("150.00")),
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")),
                new BrokerHolding(Market.US, "TQQQ", new BigDecimal("5"), new BigDecimal("60.00"))
        );
        List<Holding> tradeGuideHoldings = List.of(
                new Holding(Market.US, "AAPL", new BigDecimal("10.000000"), new BigDecimal("140.00")),
                new Holding(Market.US, "SOXL", new BigDecimal("25"), new BigDecimal("21.00")),
                new Holding(Market.US, "SPY", new BigDecimal("2"), new BigDecimal("500.00"))
        );

        List<BrokerHoldingPreviewItem> items = calculator.compare(brokerHoldings, tradeGuideHoldings);

        assertThat(items).extracting(BrokerHoldingPreviewItem::ticker)
                .containsExactly("AAPL", "SOXL", "SPY", "TQQQ");
        assertThat(items).extracting(BrokerHoldingPreviewItem::comparison)
                .containsExactly(
                        BrokerHoldingComparison.MATCHED,
                        BrokerHoldingComparison.QUANTITY_MISMATCH,
                        BrokerHoldingComparison.ONLY_IN_TRADE_GUIDE,
                        BrokerHoldingComparison.ONLY_IN_BROKER
                );
    }

    @Test
    void keepsBothQuantitiesForMismatchAndLeavesMissingSideNull() {
        List<BrokerHoldingPreviewItem> items = calculator.compare(
                List.of(new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00"))),
                List.of(new Holding(Market.US, "SPY", new BigDecimal("2"), new BigDecimal("500.00")))
        );

        BrokerHoldingPreviewItem brokerOnly = items.stream()
                .filter(item -> item.ticker().equals("SOXL"))
                .findFirst()
                .orElseThrow();
        BrokerHoldingPreviewItem tradeGuideOnly = items.stream()
                .filter(item -> item.ticker().equals("SPY"))
                .findFirst()
                .orElseThrow();

        assertThat(brokerOnly.brokerQuantity()).isEqualByComparingTo("30");
        assertThat(brokerOnly.tradeGuideQuantity()).isNull();
        assertThat(tradeGuideOnly.brokerQuantity()).isNull();
        assertThat(tradeGuideOnly.brokerAveragePurchasePrice()).isNull();
        assertThat(tradeGuideOnly.tradeGuideQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void matchesTickersCaseInsensitivelyWithinTheSameMarket() {
        List<BrokerHoldingPreviewItem> items = calculator.compare(
                List.of(new BrokerHolding(Market.US, "aapl", new BigDecimal("10"), new BigDecimal("150.00"))),
                List.of(new Holding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("140.00")))
        );

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().comparison()).isEqualTo(BrokerHoldingComparison.MATCHED);
    }

    @Test
    void treatsSameTickerInDifferentMarketsAsDifferentAssets() {
        List<BrokerHoldingPreviewItem> items = calculator.compare(
                List.of(new BrokerHolding(Market.KR, "005930", new BigDecimal("10"), new BigDecimal("70000"))),
                List.of(new Holding(Market.US, "005930", new BigDecimal("10"), new BigDecimal("70000")))
        );

        assertThat(items).hasSize(2);
        assertThat(items).extracting(BrokerHoldingPreviewItem::comparison)
                .containsExactlyInAnyOrder(
                        BrokerHoldingComparison.ONLY_IN_BROKER,
                        BrokerHoldingComparison.ONLY_IN_TRADE_GUIDE
                );
    }
}
