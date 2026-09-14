import type {BrokerProvider} from "./brokerConnection";

export type BrokerOrderImportRunStatus = "RUNNING" | "STAGED" | "FAILED";

export type BrokerOrderReconciliationStatus =
    | "MATCHED"
    | "MISMATCHED"
    | "NOT_AVAILABLE"
    | "REPLAY_FAILED";

export type BrokerOrderStagingStatus =
    | "STAGED"
    | "SKIPPED_NOT_FILLED"
    | "PENDING_SETTLEMENT"
    | "SKIPPED_CONTROL_RECORD"
    | "SKIPPED_UNSUPPORTED"
    | "ALREADY_IMPORTED"
    | "MANUAL_OVERLAP_SUSPECTED"
    | "DUPLICATE_SUSPECTED";

export type BrokerOrderSkipReason =
    | "NOT_FILLED"
    | "PARTIAL_FILL_PENDING"
    | "CONTROL_RECORD"
    | "MISSING_EXECUTION_TIME"
    | "MISSING_AVERAGE_PRICE"
    | "UNKNOWN_STATUS"
    | "ALREADY_IMPORTED"
    | "MANUAL_OVERLAP"
    | "DUPLICATE_FINGERPRINT";

export type BrokerOrderLifecycle =
    | "NOT_FILLED"
    | "PARTIALLY_FILLED_OPEN"
    | "TERMINAL_WITH_FILL"
    | "TERMINAL_WITHOUT_FILL"
    | "CONTROL_RECORD"
    | "UNKNOWN";

export type BrokerOrderSide = "BUY" | "SELL";

export type BrokerOrderImportCounts = {
    fetchedCount: number;
    stagedCount: number;
    notFilledCount: number;
    pendingSettlementCount: number;
    controlRecordCount: number;
    missingExecutionTimeCount: number;
    missingAveragePriceCount: number;
    unknownStatusCount: number;
    unsupportedMarketCount: number;
    unsupportedCurrencyCount: number;
    alreadyImportedCount: number;
    manualOverlapSuspectedCount: number;
    duplicateSuspectedCount: number;
    unknownEnumCount: number;
    duplicateFetchCount: number;
    amountMismatchCount: number;
    feeUnknownCount: number;
    buyTaxCount: number;
    openOrderCount: number;
};

export type BrokerOrderImportCoverage = {
    fullyCovered: boolean;
    coveredOrderedTo: string | null;
    nextOrderedFrom: string | null;
};

export type BrokerOrderImportRun = {
    id: number;
    provider: BrokerProvider;
    brokerConnectionId: number;
    maskedAccountNumber: string;
    requestedOrderedFrom: string;
    requestedOrderedTo: string;
    queriedOrderedFrom: string;
    queriedOrderedTo: string;
    startedAt: string;
    finishedAt: string;
    status: BrokerOrderImportRunStatus;
    counts: BrokerOrderImportCounts;
    reconciliationStatus: BrokerOrderReconciliationStatus;
    reconciliationSnapshotSyncedAt: string | null;
    failureCode: string | null;
    providerRequestId: string | null;
    executedByMemberId: number;
    coverage?: BrokerOrderImportCoverage;
};

export type BrokerOrderImportItem = {
    id: number;
    externalOrderId: string;
    market: "US" | "KR";
    ticker: string;
    displayName: string;
    orderSide: BrokerOrderSide;
    providerStatusCode: string;
    providerOrderType: string;
    providerTimeInForce: string;
    lifecycle: BrokerOrderLifecycle;
    orderedQuantity: number;
    filledQuantity: number;
    averageFilledPrice: number | null;
    filledAmount: number | null;
    commission: number | null;
    tax: number | null;
    currencyCode: string;
    orderedAt: string;
    filledAt: string | null;
    settlementDate: string | null;
    stagingStatus: BrokerOrderStagingStatus;
    skipReasonCode: BrokerOrderSkipReason | null;
    amountMismatch: boolean;
    feeUnknown: boolean;
    buyTax: boolean;
};

export type BrokerOrderImportReconciliationLine = {
    market: "US" | "KR";
    ticker: string;
    reconstructedQuantity: number;
    snapshotQuantity: number;
    quantityDifference: number;
};

export type BrokerOrderImportApprovalBlocker =
    | "RUN_NOT_STAGED"
    | "RECONCILIATION_MISMATCHED"
    | "RECONCILIATION_REPLAY_FAILED"
    | "NO_STAGED_ITEMS"
    | "ALL_BEFORE_BASELINE"
    | "INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED";

export type BrokerOrderImportApprovalAssessment = {
    stagedCount: number;
    eligibleCount: number;
    baselineExcludedCount: number;
    alreadyLinkedCount: number;
    writableCount: number;
    excludedCount: number;
    suspectedCount: number;
    overrideAllowedCount?: number;
    overrideKeptExcludedCount?: number;
    unresolvedSuspectedCount?: number;
    amountMismatchCount: number;
    baselineAt: string | null;
    fullyCovered: boolean;
    coverageAcknowledgementRequired: boolean;
    approvable: boolean;
    blocker: BrokerOrderImportApprovalBlocker | null;
};

export type BrokerOrderImportRunDetail = {
    run: BrokerOrderImportRun;
    approval: BrokerOrderImportApprovalAssessment;
    reconciliation: BrokerOrderImportReconciliationLine[];
};

export type BrokerOrderImportCreateRequest = {
    orderedFrom: string;
    orderedTo: string;
};

export type BrokerOrderApprovalResponse = {
    runId: number;
    eligibleCount: number;
    baselineExcludedCount: number;
    alreadyLinkedCount: number;
    writtenCount: number;
    approvedAt: string;
    approvedByMemberId: number;
    baselineAt: string | null;
    coverageAcknowledged?: boolean;
    overrideAllowedCount?: number;
};

export type BrokerOrderOverrideDecision = "ALLOW_LEDGER_WRITE" | "KEEP_EXCLUDED";

export type BrokerOrderImportItemOverrideRequest = {
    decision: BrokerOrderOverrideDecision;
    reason: string;
};

export type BrokerOrderImportItemOverrideResponse = {
    id: number;
    runId: number;
    itemId: number;
    externalOrderId: string;
    originalStagingStatus: BrokerOrderStagingStatus;
    originalSkipReasonCode: BrokerOrderSkipReason | null;
    decision: BrokerOrderOverrideDecision;
    resultingStagingStatus: BrokerOrderStagingStatus;
    reason: string;
    createdByMemberId: number;
    createdAt: string;
};
