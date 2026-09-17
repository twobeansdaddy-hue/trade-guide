package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderSkipReason;
import com.tradeguide.domain.broker.BrokerOrderStagedOrder;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerReconciliationReasonCode;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정합성 점검 줄 하나의 차이 사유 후보 판정을 검증한다. 어떤 경우에도 증권사를 호출하지
 * 않고, 이미 만들어 둔 주문 이력 실행·개시 잔고 기준 시각·제공자 카탈로그만 읽는다.
 */
class BrokerReconciliationReasonResolverTest {

    private static final Instant FILLED_BEFORE_BASELINE = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant FILLED_AFTER_BASELINE = Instant.parse("2026-09-05T00:00:00Z");
    private static final Instant BASELINE = Instant.parse("2026-09-01T00:00:00Z");

    private final BrokerReconciliationReasonResolver resolver =
            new BrokerReconciliationReasonResolver(new BrokerProviderRegistry(List.of(), List.of(), List.of(), List.of()));

    @Test
    void returnsNoReasonsWhenComparisonIsMatched() {
        BrokerHoldingPreviewItem item = previewItem("AAPL", Market.US, BrokerHoldingComparison.MATCHED);

        Set<BrokerReconciliationReasonCode> reasons = resolver.resolve(
                item, BrokerProvider.TOSS_SECURITIES, null, List.of(), Set.of(), false);

        assertThat(reasons).isEmpty();
    }

    @Test
    void flagsUnapprovedRunExistsWhenAStagedUnlinkedOrderIsAfterBaseline() {
        BrokerHoldingPreviewItem item = previewItem("AAPL", Market.US, BrokerHoldingComparison.QUANTITY_MISMATCH);
        BrokerOrderImportItem stagedOrder = stagedItem(
                "order-1", Market.US, "AAPL", BrokerOrderStagingStatus.STAGED, FILLED_AFTER_BASELINE);

        Set<BrokerReconciliationReasonCode> reasons = resolver.resolve(
                item, BrokerProvider.TOSS_SECURITIES, BASELINE, List.of(stagedOrder), Set.of(), false);

        assertThat(reasons).containsExactly(BrokerReconciliationReasonCode.UNAPPROVED_RUN_EXISTS);
    }

    @Test
    void flagsBaselineExcludedHistoryWhenTheStagedOrderIsBeforeBaseline() {
        BrokerHoldingPreviewItem item = previewItem("AAPL", Market.US, BrokerHoldingComparison.QUANTITY_MISMATCH);
        BrokerOrderImportItem stagedOrder = stagedItem(
                "order-1", Market.US, "AAPL", BrokerOrderStagingStatus.STAGED, FILLED_BEFORE_BASELINE);

        Set<BrokerReconciliationReasonCode> reasons = resolver.resolve(
                item, BrokerProvider.TOSS_SECURITIES, BASELINE, List.of(stagedOrder), Set.of(), false);

        assertThat(reasons).containsExactly(BrokerReconciliationReasonCode.BASELINE_EXCLUDED_HISTORY);
    }

    @Test
    void ignoresAStagedOrderThatIsAlreadyLinkedToTheLedger() {
        BrokerHoldingPreviewItem item = previewItem("AAPL", Market.US, BrokerHoldingComparison.QUANTITY_MISMATCH);
        BrokerOrderImportItem stagedOrder = stagedItem(
                "order-1", Market.US, "AAPL", BrokerOrderStagingStatus.STAGED, FILLED_AFTER_BASELINE);

        Set<BrokerReconciliationReasonCode> reasons = resolver.resolve(
                item, BrokerProvider.TOSS_SECURITIES, BASELINE, List.of(stagedOrder), Set.of("order-1"), false);

        assertThat(reasons).containsExactly(BrokerReconciliationReasonCode.UNEXPLAINED_DIFFERENCE);
    }

    @Test
    void flagsUnsettledOrPartialFillWhenAPendingSettlementOrderExistsForTheAsset() {
        BrokerHoldingPreviewItem item = previewItem("AAPL", Market.US, BrokerHoldingComparison.QUANTITY_MISMATCH);
        BrokerOrderImportItem pendingOrder = stagedItem(
                "order-1", Market.US, "AAPL", BrokerOrderStagingStatus.PENDING_SETTLEMENT, FILLED_AFTER_BASELINE);

        Set<BrokerReconciliationReasonCode> reasons = resolver.resolve(
                item, BrokerProvider.TOSS_SECURITIES, BASELINE, List.of(pendingOrder), Set.of(), false);

        assertThat(reasons).containsExactly(BrokerReconciliationReasonCode.UNSETTLED_OR_PARTIAL_FILL);
    }

    @Test
    void flagsOutOfPeriodHistoryWhenThereIsACoverageGap() {
        BrokerHoldingPreviewItem item = previewItem("AAPL", Market.US, BrokerHoldingComparison.ONLY_IN_BROKER);

        Set<BrokerReconciliationReasonCode> reasons = resolver.resolve(
                item, BrokerProvider.TOSS_SECURITIES, null, List.of(), Set.of(), true);

        assertThat(reasons).containsExactly(BrokerReconciliationReasonCode.OUT_OF_PERIOD_HISTORY);
    }

    @Test
    void flagsLedgerMarketUnsupportedForAMarketOutsideLedgerWritableMarkets() {
        BrokerHoldingPreviewItem item = previewItem("005930", Market.KR, BrokerHoldingComparison.ONLY_IN_BROKER);

        Set<BrokerReconciliationReasonCode> reasons = resolver.resolve(
                item, BrokerProvider.TOSS_SECURITIES, null, List.of(), Set.of(), false);

        assertThat(reasons).containsExactly(BrokerReconciliationReasonCode.LEDGER_MARKET_UNSUPPORTED);
    }

    @Test
    void fallsBackToUnexplainedDifferenceWhenNothingElseApplies() {
        BrokerHoldingPreviewItem item = previewItem("AAPL", Market.US, BrokerHoldingComparison.QUANTITY_MISMATCH);

        Set<BrokerReconciliationReasonCode> reasons = resolver.resolve(
                item, BrokerProvider.TOSS_SECURITIES, null, List.of(), Set.of(), false);

        assertThat(reasons).containsExactly(BrokerReconciliationReasonCode.UNEXPLAINED_DIFFERENCE);
    }

    @Test
    void combinesMultipleReasonsWhenMoreThanOneConditionApplies() {
        BrokerHoldingPreviewItem item = previewItem("005930", Market.KR, BrokerHoldingComparison.ONLY_IN_BROKER);

        Set<BrokerReconciliationReasonCode> reasons = resolver.resolve(
                item, BrokerProvider.TOSS_SECURITIES, null, List.of(), Set.of(), true);

        assertThat(reasons).containsExactlyInAnyOrder(
                BrokerReconciliationReasonCode.LEDGER_MARKET_UNSUPPORTED,
                BrokerReconciliationReasonCode.OUT_OF_PERIOD_HISTORY
        );
    }

    private BrokerHoldingPreviewItem previewItem(String ticker, Market market, BrokerHoldingComparison comparison) {
        return new BrokerHoldingPreviewItem(
                market, ticker, ticker, new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("5"), comparison);
    }

    /** 최소한의 실행 하나에 주문 한 건을 스테이징해 실제 {@link BrokerOrderImportItem}을 얻는다. */
    private BrokerOrderImportItem stagedItem(
            String externalOrderId,
            Market market,
            String ticker,
            BrokerOrderStagingStatus stagingStatus,
            Instant filledAt
    ) {
        Member member = new Member("broker@example.com", "broker-user");
        Portfolio portfolio = new Portfolio(member, "성장 포트폴리오");
        BrokerConnection connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        BrokerAccount account = new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1);
        connection.reconcileVerifiedAccounts(List.of(account));
        connection.markConnected("*****1234");

        BrokerOrderRecord record = new BrokerOrderRecord(
                externalOrderId,
                market,
                ticker,
                BrokerOrderSide.BUY,
                BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "체결",
                "지정가",
                "DAY",
                "USD",
                new BigDecimal("10"),
                new BigDecimal("10"),
                new BigDecimal("100.00"),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                filledAt.minusSeconds(3600),
                filledAt,
                LocalDate.of(2026, 9, 3)
        );

        BrokerOrderStagedOrder stagedOrder = new BrokerOrderStagedOrder(
                record,
                ticker,
                stagingStatus,
                stagingStatus == BrokerOrderStagingStatus.STAGED ? null : BrokerOrderSkipReason.PARTIAL_FILL_PENDING,
                "fingerprint-" + externalOrderId,
                false,
                false,
                false
        );

        BrokerOrderImportRun run = BrokerOrderImportRun.staged(
                portfolio,
                connection,
                connection.getAccounts().getFirst(),
                member,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 9, 7),
                LocalDateTime.of(2026, 9, 8, 9, 0),
                LocalDateTime.of(2026, 9, 8, 9, 1),
                BrokerOrderImportCounts.empty(),
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                null,
                List.of(stagedOrder),
                List.of(),
                null
        );

        return run.getItems().getFirst();
    }
}
