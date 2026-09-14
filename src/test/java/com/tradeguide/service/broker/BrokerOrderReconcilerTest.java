package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderReconciliationLineValue;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderSkipReason;
import com.tradeguide.domain.broker.BrokerOrderStagedOrder;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.service.holding.HoldingCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerOrderReconcilerTest {

    private static final Instant ORDERED_AT = Instant.parse("2026-09-01T00:30:00Z");
    private static final Instant FILLED_AT = Instant.parse("2026-09-01T13:30:00Z");

    private final HoldingCalculator holdingCalculator = new HoldingCalculator();
    private final BrokerOrderReconciler reconciler = new BrokerOrderReconciler(holdingCalculator);

    @Test
    void reportsMatchedWhenReconstructedQuantitiesEqualTheBrokerSnapshot() {
        BrokerOrderReconciler.Result result = reconciler.reconcile(
                List.of(),
                List.of(staged(buy("order-1", "10", "100.00"))),
                Map.of(asset("AAPL"), new BigDecimal("10"))
        );

        assertThat(result.status()).isEqualTo(BrokerOrderReconciliationStatus.MATCHED);
        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.reconstructedQuantity()).isEqualByComparingTo("10");
            assertThat(line.snapshotQuantity()).isEqualByComparingTo("10");
            assertThat(line.quantityDifference()).isEqualByComparingTo("0");
        });
    }

    /**
     * 이력 앞부분이 잘리거나 앱에서 낸 주문이 응답에 빠지면 재구성 수량이 모자란다.
     * 이 차이가 유일한 관측 수단이므로, 조용히 통과시키지 않는 것이 핵심이다.
     */
    @Test
    void reportsMismatchWhenTheReconstructedQuantityFallsShortOfTheSnapshot() {
        BrokerOrderReconciler.Result result = reconciler.reconcile(
                List.of(),
                List.of(staged(buy("order-1", "4", "100.00"))),
                Map.of(asset("AAPL"), new BigDecimal("10"))
        );

        assertThat(result.status()).isEqualTo(BrokerOrderReconciliationStatus.MISMATCHED);
        assertThat(result.lines()).singleElement().satisfies(line ->
                assertThat(line.quantityDifference()).isEqualByComparingTo("-6"));
    }

    /** 이중 계상은 반대 방향으로 나타난다. 재구성 수량이 스냅샷을 넘는다. */
    @Test
    void reportsMismatchWhenTheReconstructedQuantityExceedsTheSnapshot() {
        BrokerOrderReconciler.Result result = reconciler.reconcile(
                List.of(),
                List.of(staged(buy("order-1", "10", "100.00")), staged(buy("order-2", "10", "100.00"))),
                Map.of(asset("AAPL"), new BigDecimal("10"))
        );

        assertThat(result.status()).isEqualTo(BrokerOrderReconciliationStatus.MISMATCHED);
        assertThat(result.lines()).singleElement().satisfies(line ->
                assertThat(line.quantityDifference()).isEqualByComparingTo("10"));
    }

    @Test
    void reportsNotAvailableWhenNoBrokerHoldingSnapshotHasBeenSaved() {
        BrokerOrderReconciler.Result result = reconciler.reconcile(
                List.of(), List.of(staged(buy("order-1", "10", "100.00"))), null);

        assertThat(result.status()).isEqualTo(BrokerOrderReconciliationStatus.NOT_AVAILABLE);
        assertThat(result.lines()).isEmpty();
    }

    /**
     * 매수 없는 매도는 이력이 앞에서 잘렸다는 가장 분명한 신호다. 재생 예외를 그대로 흘리면
     * 요청이 오류로 끝나 사용자가 아무것도 볼 수 없고, 삼키면 "이상 없음"으로 보인다.
     * 상태로 올려 보고한다.
     */
    @Test
    void reportsReplayFailedWhenTruncatedHistoryProducesASellWithoutAMatchingBuy() {
        BrokerOrderReconciler.Result result = reconciler.reconcile(
                List.of(),
                List.of(staged(sell("order-1", "10", "100.00"))),
                Map.of(asset("AAPL"), new BigDecimal("0"))
        );

        assertThat(result.status()).isEqualTo(BrokerOrderReconciliationStatus.REPLAY_FAILED);
        assertThat(result.lines()).isEmpty();
    }

    /** 반영 후보가 아닌 항목까지 재생에 넣으면 보류 중인 부분 체결이 보유 수량으로 잡힌다. */
    @Test
    void replaysOnlyStagedOrdersAndIgnoresExcludedOnes() {
        BrokerOrderStagedOrder pending = new BrokerOrderStagedOrder(
                buy("order-2", "5", "100.00"), "AAPL",
                BrokerOrderStagingStatus.PENDING_SETTLEMENT, BrokerOrderSkipReason.PARTIAL_FILL_PENDING,
                "fingerprint-2", false, false, false);

        BrokerOrderReconciler.Result result = reconciler.reconcile(
                List.of(),
                List.of(staged(buy("order-1", "10", "100.00")), pending),
                Map.of(asset("AAPL"), new BigDecimal("10"))
        );

        assertThat(result.status()).isEqualTo(BrokerOrderReconciliationStatus.MATCHED);
    }

    /** 기존 원장 행도 재생에 들어간다. 스테이징분만 세면 이미 반영된 보유분이 통째로 빠진다. */
    @Test
    void includesTheExistingLedgerInTheReplay() {
        TradeTransaction existing = new TradeTransaction(
                null, Market.US, "AAPL", TradeType.BUY,
                new BigDecimal("4"), new BigDecimal("90.00"), BigDecimal.ZERO,
                Instant.parse("2026-08-01T13:30:00Z"));

        BrokerOrderReconciler.Result result = reconciler.reconcile(
                List.of(existing),
                List.of(staged(buy("order-1", "6", "100.00"))),
                Map.of(asset("AAPL"), new BigDecimal("10"))
        );

        assertThat(result.status()).isEqualTo(BrokerOrderReconciliationStatus.MATCHED);
    }

    /** 한쪽에만 있는 종목이 가장 중요한 차이다. 교집합만 비교하면 그 종목이 통째로 사라진다. */
    @Test
    void reportsAssetsThatExistOnOnlyOneSide() {
        BrokerOrderReconciler.Result result = reconciler.reconcile(
                List.of(),
                List.of(staged(buy("order-1", "10", "100.00"))),
                Map.of(new BrokerOrderReconciler.AssetKey(Market.US, "MSFT"), new BigDecimal("3"))
        );

        assertThat(result.status()).isEqualTo(BrokerOrderReconciliationStatus.MISMATCHED);
        assertThat(result.lines()).extracting(BrokerOrderReconciliationLineValue::ticker)
                .containsExactly("AAPL", "MSFT");
    }

    /**
     * 제공자는 분할 체결을 주문 단위로 집계해서 준다. 개별 체결가·체결 시각은 복원할 수 없다.
     * 그래도 보유 수량과 평단만 놓고 보면 손실이 없어야 한다. 이 등식이 깨지면 집계 1행으로
     * 원장을 만들겠다는 이 설계의 전제 자체가 무너지므로, 계산기 쪽 변경도 여기서 잡힌다.
     */
    @Test
    void aggregatedOrderProducesTheSameHoldingAsTheIndividualFillsItSummarizes() {
        Instant lastFill = Instant.parse("2026-09-01T13:30:00Z");

        List<TradeTransaction> individualFills = List.of(
                new TradeTransaction(null, Market.US, "AAPL", TradeType.BUY,
                        new BigDecimal("3"), new BigDecimal("100.00"), new BigDecimal("0.60"),
                        Instant.parse("2026-09-01T13:10:00Z")),
                new TradeTransaction(null, Market.US, "AAPL", TradeType.BUY,
                        new BigDecimal("7"), new BigDecimal("110.00"), new BigDecimal("1.40"),
                        lastFill)
        );

        // 평균 체결가 = (3*100 + 7*110) / 10 = 107, 총 수수료 = 2.00
        List<TradeTransaction> aggregated = List.of(
                new TradeTransaction(null, Market.US, "AAPL", TradeType.BUY,
                        new BigDecimal("10"), new BigDecimal("107.00"), new BigDecimal("2.00"), lastFill)
        );

        Holding fromIndividualFills = holdingCalculator.calculate(individualFills).getFirst();
        Holding fromAggregate = holdingCalculator.calculate(aggregated).getFirst();

        assertThat(fromAggregate.getQuantity()).isEqualByComparingTo(fromIndividualFills.getQuantity());
        assertThat(fromAggregate.getAveragePurchasePrice())
                .isEqualByComparingTo(fromIndividualFills.getAveragePurchasePrice());
    }

    private BrokerOrderReconciler.AssetKey asset(String ticker) {
        return new BrokerOrderReconciler.AssetKey(Market.US, ticker);
    }

    private BrokerOrderStagedOrder staged(BrokerOrderRecord record) {
        return new BrokerOrderStagedOrder(
                record, record.ticker(), BrokerOrderStagingStatus.STAGED, null,
                "fingerprint-" + record.externalOrderId(), false, false, false);
    }

    private BrokerOrderRecord buy(String orderId, String quantity, String price) {
        return record(orderId, BrokerOrderSide.BUY, quantity, price);
    }

    private BrokerOrderRecord sell(String orderId, String quantity, String price) {
        return record(orderId, BrokerOrderSide.SELL, quantity, price);
    }

    private BrokerOrderRecord record(String orderId, BrokerOrderSide side, String quantity, String price) {
        return new BrokerOrderRecord(
                orderId,
                Market.US,
                "AAPL",
                side,
                BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED",
                "LIMIT",
                "DAY",
                "USD",
                new BigDecimal(quantity),
                new BigDecimal(quantity),
                new BigDecimal(price),
                new BigDecimal(quantity).multiply(new BigDecimal(price)),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ORDERED_AT,
                FILLED_AT,
                null
        );
    }
}
