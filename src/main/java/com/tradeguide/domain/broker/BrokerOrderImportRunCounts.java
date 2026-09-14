package com.tradeguide.domain.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * {@link BrokerOrderImportCounts}의 영속 표현이다. 실행 엔티티에 열아홉 개 컬럼을 늘어놓는 대신
 * 한 덩어리로 묶어, 새 사유가 생겼을 때 고칠 곳을 한 군데로 남긴다.
 *
 * <p>불변식 검사는 {@link BrokerOrderImportCounts} 쪽에만 둔다. 저장 표현이 검사까지 하면
 * 같은 규칙이 두 곳에 살게 되고, 둘이 갈라지는 순간 어느 쪽이 맞는지 알 수 없게 된다.
 */
@Embeddable
public class BrokerOrderImportRunCounts {

    @Column(name = "fetched_count", nullable = false)
    private int fetchedCount;

    @Column(name = "staged_count", nullable = false)
    private int stagedCount;

    @Column(name = "not_filled_count", nullable = false)
    private int notFilledCount;

    @Column(name = "pending_settlement_count", nullable = false)
    private int pendingSettlementCount;

    @Column(name = "control_record_count", nullable = false)
    private int controlRecordCount;

    @Column(name = "missing_execution_time_count", nullable = false)
    private int missingExecutionTimeCount;

    @Column(name = "missing_average_price_count", nullable = false)
    private int missingAveragePriceCount;

    @Column(name = "unknown_status_count", nullable = false)
    private int unknownStatusCount;

    @Column(name = "unsupported_market_count", nullable = false)
    private int unsupportedMarketCount;

    @Column(name = "unsupported_currency_count", nullable = false)
    private int unsupportedCurrencyCount;

    @Column(name = "already_imported_count", nullable = false)
    private int alreadyImportedCount;

    @Column(name = "manual_overlap_suspected_count", nullable = false)
    private int manualOverlapSuspectedCount;

    @Column(name = "duplicate_suspected_count", nullable = false)
    private int duplicateSuspectedCount;

    @Column(name = "unknown_enum_count", nullable = false)
    private int unknownEnumCount;

    @Column(name = "duplicate_fetch_count", nullable = false)
    private int duplicateFetchCount;

    @Column(name = "amount_mismatch_count", nullable = false)
    private int amountMismatchCount;

    @Column(name = "fee_unknown_count", nullable = false)
    private int feeUnknownCount;

    @Column(name = "buy_tax_count", nullable = false)
    private int buyTaxCount;

    @Column(name = "open_order_count", nullable = false)
    private int openOrderCount;

    protected BrokerOrderImportRunCounts() {
    }

    static BrokerOrderImportRunCounts of(BrokerOrderImportCounts counts) {
        BrokerOrderImportRunCounts persisted = new BrokerOrderImportRunCounts();
        persisted.fetchedCount = counts.fetchedCount();
        persisted.stagedCount = counts.stagedCount();
        persisted.notFilledCount = counts.notFilledCount();
        persisted.pendingSettlementCount = counts.pendingSettlementCount();
        persisted.controlRecordCount = counts.controlRecordCount();
        persisted.missingExecutionTimeCount = counts.missingExecutionTimeCount();
        persisted.missingAveragePriceCount = counts.missingAveragePriceCount();
        persisted.unknownStatusCount = counts.unknownStatusCount();
        persisted.unsupportedMarketCount = counts.unsupportedMarketCount();
        persisted.unsupportedCurrencyCount = counts.unsupportedCurrencyCount();
        persisted.alreadyImportedCount = counts.alreadyImportedCount();
        persisted.manualOverlapSuspectedCount = counts.manualOverlapSuspectedCount();
        persisted.duplicateSuspectedCount = counts.duplicateSuspectedCount();
        persisted.unknownEnumCount = counts.unknownEnumCount();
        persisted.duplicateFetchCount = counts.duplicateFetchCount();
        persisted.amountMismatchCount = counts.amountMismatchCount();
        persisted.feeUnknownCount = counts.feeUnknownCount();
        persisted.buyTaxCount = counts.buyTaxCount();
        persisted.openOrderCount = counts.openOrderCount();
        return persisted;
    }

    BrokerOrderImportCounts toCounts() {
        return new BrokerOrderImportCounts(
                fetchedCount,
                stagedCount,
                notFilledCount,
                pendingSettlementCount,
                controlRecordCount,
                missingExecutionTimeCount,
                missingAveragePriceCount,
                unknownStatusCount,
                unsupportedMarketCount,
                unsupportedCurrencyCount,
                alreadyImportedCount,
                manualOverlapSuspectedCount,
                duplicateSuspectedCount,
                unknownEnumCount,
                duplicateFetchCount,
                amountMismatchCount,
                feeUnknownCount,
                buyTaxCount,
                openOrderCount
        );
    }
}
