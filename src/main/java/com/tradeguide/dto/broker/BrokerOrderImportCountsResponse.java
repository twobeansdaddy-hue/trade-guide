package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerOrderImportCounts;

/**
 * 사유별 건수 응답이다. 화면이 "제외 3건"이 아니라 "부분 체결 보류 2건, 이미 반영 1건"으로
 * 말할 수 있어야 사용자가 다음에 무엇을 할지 정할 수 있다.
 */
public record BrokerOrderImportCountsResponse(
        int fetchedCount,
        int stagedCount,
        int notFilledCount,
        int pendingSettlementCount,
        int controlRecordCount,
        int missingExecutionTimeCount,
        int missingAveragePriceCount,
        int unknownStatusCount,
        int unsupportedMarketCount,
        int unsupportedCurrencyCount,
        int alreadyImportedCount,
        int manualOverlapSuspectedCount,
        int duplicateSuspectedCount,
        int unknownEnumCount,
        int duplicateFetchCount,
        int amountMismatchCount,
        int feeUnknownCount,
        int buyTaxCount,
        int openOrderCount
) {
    public static BrokerOrderImportCountsResponse from(BrokerOrderImportCounts counts) {
        return new BrokerOrderImportCountsResponse(
                counts.fetchedCount(),
                counts.stagedCount(),
                counts.notFilledCount(),
                counts.pendingSettlementCount(),
                counts.controlRecordCount(),
                counts.missingExecutionTimeCount(),
                counts.missingAveragePriceCount(),
                counts.unknownStatusCount(),
                counts.unsupportedMarketCount(),
                counts.unsupportedCurrencyCount(),
                counts.alreadyImportedCount(),
                counts.manualOverlapSuspectedCount(),
                counts.duplicateSuspectedCount(),
                counts.unknownEnumCount(),
                counts.duplicateFetchCount(),
                counts.amountMismatchCount(),
                counts.feeUnknownCount(),
                counts.buyTaxCount(),
                counts.openOrderCount()
        );
    }
}
