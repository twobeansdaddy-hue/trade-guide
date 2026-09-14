package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerOrderFingerprintTest {

    @Test
    void producesTheSameFingerprintForTheSameContent() {
        assertThat(BrokerOrderFingerprint.of(record("order-1", "10", "100.25")))
                .isEqualTo(BrokerOrderFingerprint.of(record("order-1", "10", "100.25")));
    }

    /**
     * 주문 식별자는 지문에 들어가지 않는다. 들어가면 "식별자만 다르고 내용은 같은" 주문을
     * 영원히 찾아낼 수 없고, 그 감지가 이 지문의 유일한 존재 이유다.
     */
    @Test
    void ignoresTheOrderIdentifierSoRenamedOrdersStillMatch() {
        assertThat(BrokerOrderFingerprint.of(record("order-1", "10", "100.25")))
                .isEqualTo(BrokerOrderFingerprint.of(record("order-2", "10", "100.25")));
    }

    /**
     * 제공자가 자릿수 표기만 바꿔도 전부 다른 주문으로 보이면 감지가 무력화된다.
     * 값이 같으면 스케일이 달라도 같은 지문이어야 한다.
     */
    @Test
    void normalizesDecimalScaleSoTrailingZeroesDoNotChangeTheFingerprint() {
        assertThat(BrokerOrderFingerprint.of(record("order-1", "10.000000", "100.2500")))
                .isEqualTo(BrokerOrderFingerprint.of(record("order-1", "10", "100.25")));
    }

    @Test
    void changesWhenFilledQuantityChanges() {
        assertThat(BrokerOrderFingerprint.of(record("order-1", "10", "100.25")))
                .isNotEqualTo(BrokerOrderFingerprint.of(record("order-1", "11", "100.25")));
    }

    @Test
    void changesWhenAverageFilledPriceChanges() {
        assertThat(BrokerOrderFingerprint.of(record("order-1", "10", "100.25")))
                .isNotEqualTo(BrokerOrderFingerprint.of(record("order-1", "10", "100.26")));
    }

    @Test
    void changesWhenTheProviderStatusChanges() {
        BrokerOrderRecord partiallyFilled = new BrokerOrderRecord(
                "order-1", Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN,
                "PARTIAL_FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T13:30:00Z"), null);

        assertThat(BrokerOrderFingerprint.of(partiallyFilled))
                .isNotEqualTo(BrokerOrderFingerprint.of(record("order-1", "10", "100.25")));
    }

    /** 빈 값과 빈 문자열이 같은 지문을 내면 서로 다른 상태가 하나로 보인다. */
    @Test
    void distinguishesMissingValuesFromEmptyStrings() {
        BrokerOrderRecord withoutFillTime = new BrokerOrderRecord(
                "order-1", Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                Instant.parse("2026-09-01T00:00:00Z"), null, null);

        assertThat(BrokerOrderFingerprint.of(withoutFillTime))
                .isNotEqualTo(BrokerOrderFingerprint.of(record("order-1", "10", "100.25")));
    }

    private BrokerOrderRecord record(String orderId, String filledQuantity, String averagePrice) {
        return new BrokerOrderRecord(
                orderId,
                Market.US,
                "AAPL",
                BrokerOrderSide.BUY,
                BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED",
                "LIMIT",
                "DAY",
                "USD",
                new BigDecimal(filledQuantity),
                new BigDecimal(filledQuantity),
                new BigDecimal(averagePrice),
                new BigDecimal("1002.50"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-01T13:30:00Z"),
                null
        );
    }
}
