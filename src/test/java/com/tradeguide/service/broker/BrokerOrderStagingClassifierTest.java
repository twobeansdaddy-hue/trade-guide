package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderSkipReason;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerOrderStagingClassifierTest {

    private static final Instant ORDERED_AT = Instant.parse("2026-09-01T00:30:00Z");
    private static final Instant FILLED_AT = Instant.parse("2026-09-01T13:30:00Z");

    private final BrokerOrderStagingClassifier classifier = new BrokerOrderStagingClassifier();

    @Test
    void stagesTerminalOrdersThatHaveAFillPriceAndTime() {
        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(filled("order-1")), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders()).singleElement().satisfies(order -> {
            assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.STAGED);
            assertThat(order.skipReason()).isNull();
        });
    }

    @Test
    void keepsNotFilledOrdersAsItemsInsteadOfDroppingThem() {
        BrokerOrderRecord notFilled = record(
                "order-1", BrokerOrderLifecycle.NOT_FILLED, "PENDING", "0", null, null, null);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(notFilled), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders()).singleElement().satisfies(order -> {
            assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.SKIPPED_NOT_FILLED);
            assertThat(order.skipReason()).isEqualTo(BrokerOrderSkipReason.NOT_FILLED);
        });
    }

    /**
     * 부분 체결은 제공자가 종료 그룹으로 돌려주더라도 보류다. "더 이상 체결되지 않는다"를
     * 알려 주는 필드가 명세에 없으므로, 종료로 단정하면 아직 남은 체결분을 잃는다.
     */
    @Test
    void holdsPartiallyFilledOrdersEvenThoughTheyCarryAFillPriceAndTime() {
        BrokerOrderRecord partial = record(
                "order-1", BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN, "PARTIAL_FILLED",
                "5", "100.25", "501.25", FILLED_AT);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(partial), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders()).singleElement().satisfies(order -> {
            assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.PENDING_SETTLEMENT);
            assertThat(order.skipReason()).isEqualTo(BrokerOrderSkipReason.PARTIAL_FILL_PENDING);
        });
    }

    /**
     * 취소·정정 거부 레코드는 원주문의 체결 집계를 복제할 수 있다. 합산하면 이중 계상이 되므로
     * 체결분이 있어도 반영 후보로 올리지 않고 건수로 보고한다.
     */
    @Test
    void excludesControlRecordsEvenWhenTheyReportAFill() {
        BrokerOrderRecord controlRecord = record(
                "order-1", BrokerOrderLifecycle.CONTROL_RECORD, "CANCEL_REJECTED",
                "10", "100.25", "1002.50", FILLED_AT);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(controlRecord), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders()).singleElement().satisfies(order -> {
            assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.SKIPPED_CONTROL_RECORD);
            assertThat(order.skipReason()).isEqualTo(BrokerOrderSkipReason.CONTROL_RECORD);
        });
    }

    /** 결제일은 체결 시각이 아니다. 없는 시각을 만들어 넣지 않는다. */
    @Test
    void excludesFilledOrdersWithoutAnExecutionTimeInsteadOfSubstitutingSettlementDate() {
        BrokerOrderRecord missingTime = record(
                "order-1", BrokerOrderLifecycle.TERMINAL_WITH_FILL, "FILLED",
                "10", "100.25", "1002.50", null);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(missingTime), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders()).singleElement().satisfies(order ->
                assertThat(order.skipReason()).isEqualTo(BrokerOrderSkipReason.MISSING_EXECUTION_TIME));
    }

    @Test
    void excludesFilledOrdersWithoutAnAveragePrice() {
        BrokerOrderRecord missingPrice = record(
                "order-1", BrokerOrderLifecycle.TERMINAL_WITH_FILL, "FILLED",
                "10", null, "1002.50", FILLED_AT);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(missingPrice), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders()).singleElement().satisfies(order ->
                assertThat(order.skipReason()).isEqualTo(BrokerOrderSkipReason.MISSING_AVERAGE_PRICE));
    }

    @Test
    void reportsUnknownProviderStatusesWithoutFailingTheWholeRun() {
        BrokerOrderRecord unknown = record(
                "order-1", BrokerOrderLifecycle.UNKNOWN, "SOMETHING_NEW",
                "10", "100.25", "1002.50", FILLED_AT);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(unknown), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders()).singleElement().satisfies(order ->
                assertThat(order.skipReason()).isEqualTo(BrokerOrderSkipReason.UNKNOWN_STATUS));
    }

    @Test
    void marksOrdersThatAreAlreadyInTheLedgerAsAlreadyImported() {
        BrokerOrderStagingClassifier.StagingContext context = new BrokerOrderStagingClassifier.StagingContext(
                Set.of("order-1"), Map.of(), List.of(), Map.of());

        BrokerOrderStagingClassifier.Result result = classifier.classify(List.of(filled("order-1")), context);

        assertThat(result.stagedOrders()).singleElement().satisfies(order ->
                assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.ALREADY_IMPORTED));
    }

    /**
     * 같은 구간을 두 번 실행했을 때 제공자가 같은 주문에 다른 식별자를 주면, 그대로 승인했을 때
     * 원장이 중복된다. 원장에 닿기 전에 이 판정이 잡아내는 것이 이 단계의 인수 조건이다.
     */
    @Test
    void flagsOrdersWhoseContentMatchesAKnownOrderUnderADifferentIdentifier() {
        BrokerOrderRecord renamed = filled("order-2");
        BrokerOrderStagingClassifier.StagingContext context = new BrokerOrderStagingClassifier.StagingContext(
                Set.of(),
                Map.of(BrokerOrderFingerprint.of(filled("order-1")), "order-1"),
                List.of(),
                Map.of()
        );

        BrokerOrderStagingClassifier.Result result = classifier.classify(List.of(renamed), context);

        assertThat(result.stagedOrders()).singleElement().satisfies(order -> {
            assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.DUPLICATE_SUSPECTED);
            assertThat(order.skipReason()).isEqualTo(BrokerOrderSkipReason.DUPLICATE_FINGERPRINT);
        });
    }

    /** 같은 식별자로 다시 온 주문은 정상 재조회다. 자기 자신을 중복으로 의심하면 안 된다. */
    @Test
    void doesNotFlagAnOrderAsDuplicateWhenItsOwnIdentifierIsUnchanged() {
        BrokerOrderStagingClassifier.StagingContext context = new BrokerOrderStagingClassifier.StagingContext(
                Set.of(),
                Map.of(BrokerOrderFingerprint.of(filled("order-1")), "order-1"),
                List.of(),
                Map.of()
        );

        BrokerOrderStagingClassifier.Result result = classifier.classify(List.of(filled("order-1")), context);

        assertThat(result.stagedOrders()).singleElement().satisfies(order ->
                assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.STAGED));
    }

    @Test
    void flagsOrdersThatLookLikeAnExistingManualTradeOnTheSameKoreanCalendarDay() {
        BrokerOrderStagingClassifier.ManualTrade manualTrade = new BrokerOrderStagingClassifier.ManualTrade(
                Market.US, "AAPL", TradeType.BUY, new BigDecimal("10"),
                Instant.parse("2026-09-01T02:00:00Z"));

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(filled("order-1")),
                new BrokerOrderStagingClassifier.StagingContext(Set.of(), Map.of(), List.of(manualTrade), Map.of())
        );

        assertThat(result.stagedOrders()).singleElement().satisfies(order -> {
            assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);
            assertThat(order.skipReason()).isEqualTo(BrokerOrderSkipReason.MANUAL_OVERLAP);
        });
    }

    /** 수량이 다르면 다른 거래다. 조건을 느슨하게 잡으면 알림이 아니라 오답이 된다. */
    @Test
    void doesNotFlagManualOverlapWhenTheQuantityDiffers() {
        BrokerOrderStagingClassifier.ManualTrade manualTrade = new BrokerOrderStagingClassifier.ManualTrade(
                Market.US, "AAPL", TradeType.BUY, new BigDecimal("9"), FILLED_AT);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(filled("order-1")),
                new BrokerOrderStagingClassifier.StagingContext(Set.of(), Map.of(), List.of(manualTrade), Map.of())
        );

        assertThat(result.stagedOrders()).singleElement().satisfies(order ->
                assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.STAGED));
    }

    @Test
    void doesNotFlagManualOverlapOnADifferentKoreanCalendarDay() {
        BrokerOrderStagingClassifier.ManualTrade manualTrade = new BrokerOrderStagingClassifier.ManualTrade(
                Market.US, "AAPL", TradeType.BUY, new BigDecimal("10"),
                Instant.parse("2026-09-03T13:30:00Z"));

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(filled("order-1")),
                new BrokerOrderStagingClassifier.StagingContext(Set.of(), Map.of(), List.of(manualTrade), Map.of())
        );

        assertThat(result.stagedOrders()).singleElement().satisfies(order ->
                assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.STAGED));
    }

    /**
     * 반올림으로 설명되는 괴리까지 신고하면 거의 모든 주문에 경고가 붙어 신호가 죽는다.
     * 평균가가 소수 둘째 자리까지면 수량 10주에 대한 최대 반올림 오차는 0.05다.
     */
    @Test
    void doesNotReportAnAmountGapThatRoundingOfTheAveragePriceExplains() {
        BrokerOrderRecord rounded = record(
                "order-1", BrokerOrderLifecycle.TERMINAL_WITH_FILL, "FILLED",
                "10", "100.25", "1002.54", FILLED_AT);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(rounded), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders().getFirst().amountMismatch()).isFalse();
    }

    @Test
    void reportsAnAmountGapThatRoundingCannotExplain() {
        BrokerOrderRecord mismatched = record(
                "order-1", BrokerOrderLifecycle.TERMINAL_WITH_FILL, "FILLED",
                "10", "100.25", "900.00", FILLED_AT);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(mismatched), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders().getFirst().amountMismatch()).isTrue();
    }

    /** 수수료 미상이라고 체결을 버리는 것이 더 나쁘다. 반영은 하되 반드시 알린다. */
    @Test
    void stagesOrdersWithAnUnknownCommissionButReportsThem() {
        BrokerOrderRecord noCommission = new BrokerOrderRecord(
                "order-1", Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                null, null, ORDERED_AT, FILLED_AT, null);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(noCommission), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders()).singleElement().satisfies(order -> {
            assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.STAGED);
            assertThat(order.feeUnknown()).isTrue();
        });
    }

    /** 매수 세금은 원장 계산에 들어가지 않는다. 그만큼 취득원가가 낮게 잡히므로 반드시 알린다. */
    @Test
    void reportsBuyOrdersThatCarryTaxBecauseTaxIsNotPartOfTheCostBasis() {
        BrokerOrderRecord taxedBuy = new BrokerOrderRecord(
                "order-1", Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                new BigDecimal("1.00"), new BigDecimal("0.30"), ORDERED_AT, FILLED_AT, null);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(taxedBuy), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders().getFirst().buyTax()).isTrue();
    }

    @Test
    void fallsBackToTheTickerWhenTheAssetCatalogueHasNoDisplayName() {
        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(filled("order-1")), BrokerOrderStagingClassifier.StagingContext.empty());

        assertThat(result.stagedOrders().getFirst().displayName()).isEqualTo("AAPL");
    }

    @Test
    void usesTheAssetCatalogueDisplayNameWhenItIsKnown() {
        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(filled("order-1")),
                new BrokerOrderStagingClassifier.StagingContext(
                        Set.of(), Map.of(), List.of(),
                        Map.of(new BrokerOrderStagingClassifier.AssetKey(Market.US, "AAPL"), "애플"))
        );

        assertThat(result.stagedOrders().getFirst().displayName()).isEqualTo("애플");
    }

    /**
     * 사유별 건수의 합이 조회 건수와 맞지 않으면 어딘가에서 주문이 조용히 사라진 것이다.
     * 새 사유를 더할 때 이 검사가 깨지도록 두는 것이 이 테스트의 목적이다.
     */
    @Test
    void keepsEveryFetchedOrderAccountedForAcrossReasonCounts() {
        List<BrokerOrderRecord> records = List.of(
                filled("order-1"),
                record("order-2", BrokerOrderLifecycle.NOT_FILLED, "CANCELED", "0", null, null, null),
                record("order-3", BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN, "PARTIAL_FILLED",
                        "5", "100.25", "501.25", FILLED_AT),
                record("order-4", BrokerOrderLifecycle.CONTROL_RECORD, "CANCEL_REJECTED",
                        "10", "100.25", "1002.50", FILLED_AT),
                record("order-5", BrokerOrderLifecycle.UNKNOWN, "SOMETHING_NEW",
                        "10", "100.25", "1002.50", FILLED_AT)
        );

        BrokerOrderImportCounts counts = classifier
                .classify(records, BrokerOrderStagingClassifier.StagingContext.empty())
                .toCounts(2, 3, 1, 4, 7);

        assertThat(counts.fetchedCount()).isEqualTo(records.size() + 2 + 3);
        assertThat(counts.classifiedCount()).isEqualTo(counts.fetchedCount());
        assertThat(counts.stagedCount()).isEqualTo(1);
        assertThat(counts.notFilledCount()).isEqualTo(1);
        assertThat(counts.pendingSettlementCount()).isEqualTo(1);
        assertThat(counts.controlRecordCount()).isEqualTo(1);
        assertThat(counts.unknownStatusCount()).isEqualTo(1);
        assertThat(counts.unsupportedMarketCount()).isEqualTo(2);
        assertThat(counts.unsupportedCurrencyCount()).isEqualTo(3);
        assertThat(counts.unknownEnumCount()).isEqualTo(1);
        assertThat(counts.duplicateFetchCount()).isEqualTo(4);
        assertThat(counts.openOrderCount()).isEqualTo(7);
    }

    /**
     * 한 주문이 여러 사유에 걸릴 수 있다. 우선순위가 흔들리면 사유별 건수가 서로 배타적이지 않게 되고
     * 합계 불변식이 깨진다. 이미 반영된 주문이 항상 먼저다.
     */
    @Test
    void prefersAlreadyImportedOverEveryOtherSuspicion() {
        BrokerOrderStagingClassifier.ManualTrade manualTrade = new BrokerOrderStagingClassifier.ManualTrade(
                Market.US, "AAPL", TradeType.BUY, new BigDecimal("10"), FILLED_AT);

        BrokerOrderStagingClassifier.Result result = classifier.classify(
                List.of(filled("order-1")),
                new BrokerOrderStagingClassifier.StagingContext(
                        Set.of("order-1"),
                        Map.of(BrokerOrderFingerprint.of(filled("order-1")), "order-9"),
                        List.of(manualTrade),
                        Map.of())
        );

        assertThat(result.stagedOrders()).singleElement().satisfies(order ->
                assertThat(order.stagingStatus()).isEqualTo(BrokerOrderStagingStatus.ALREADY_IMPORTED));
    }

    private BrokerOrderRecord filled(String orderId) {
        return record(orderId, BrokerOrderLifecycle.TERMINAL_WITH_FILL, "FILLED",
                "10", "100.25", "1002.50", FILLED_AT);
    }

    private BrokerOrderRecord record(
            String orderId,
            BrokerOrderLifecycle lifecycle,
            String providerStatusCode,
            String filledQuantity,
            String averagePrice,
            String filledAmount,
            Instant filledAt
    ) {
        return new BrokerOrderRecord(
                orderId,
                Market.US,
                "AAPL",
                BrokerOrderSide.BUY,
                lifecycle,
                providerStatusCode,
                "LIMIT",
                "DAY",
                "USD",
                new BigDecimal("10"),
                new BigDecimal(filledQuantity),
                averagePrice == null ? null : new BigDecimal(averagePrice),
                filledAmount == null ? null : new BigDecimal(filledAmount),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ORDERED_AT,
                filledAt,
                null
        );
    }
}
