import type {BrokerProvider} from "./brokerConnection";

export type BrokerLinkCandidate = {
    brokerConnectionId: number;
    provider: string;
    displayName: string;
    brokerAccountId: number;
    maskedAccountNumber: string;
    accountType: string;
    lastVerifiedAt: string | null;
}

export type PortfolioBrokerLink = {
    id: number;
    brokerConnectionId: number;
    provider: string;
    displayName: string;
    brokerAccountId: number;
    maskedAccountNumber: string;
    accountType: string;
    linkedAt: string;
    updatedAt: string;
}

export type BrokerHoldingComparison =
    | "MATCHED"
    | "QUANTITY_MISMATCH"
    | "ONLY_IN_BROKER"
    | "ONLY_IN_TRADE_GUIDE";

export type BrokerHoldingPreviewItem = {
    market: string;
    ticker: string;
    displayName: string;
    brokerQuantity: number | null;
    brokerAveragePurchasePrice: number | null;
    tradeGuideQuantity: number | null;
    comparison: BrokerHoldingComparison;
    snapshotItemId: number | null;
}

export type BrokerHoldingPreview = {
    provider: BrokerProvider;
    brokerConnectionId: number;
    maskedAccountNumber: string;
    syncedAt: string;
    items: BrokerHoldingPreviewItem[];
    unsupportedMarketCount: number;
}

export type BrokerHoldingSnapshotItem = {
    market: string;
    ticker: string;
    displayName: string;
    quantity: number;
    averagePurchasePrice: number;
}

export type BrokerHoldingSnapshot = {
    id: number;
    provider: BrokerProvider;
    brokerConnectionId: number;
    maskedAccountNumber: string;
    syncedAt: string;
    items: BrokerHoldingSnapshotItem[];
    unsupportedMarketCount: number;
}

export type PortfolioBrokerHoldingImportStatus = "ACTIVE" | "REVOKED";

export type PortfolioBrokerHoldingImport = {
    id: number;
    snapshotItemId: number | null;
    market: string;
    ticker: string;
    displayName: string;
    quantity: number;
    averagePurchasePrice: number;
    snapshotSyncedAt: string;
    tradeTransactionId: number;
    approvedByMemberId: number;
    approvedAt: string;
    status: PortfolioBrokerHoldingImportStatus;
}

export type BrokerHistoryPage<T> = {
    items: T[];
    page: number;
    size: number;
    totalElements: number;
    hasNext: boolean;
}

export type BrokerOpeningBalanceSkipReason =
    | "NOT_ONLY_IN_BROKER"
    | "UNSUPPORTED_MARKET"
    | "ALREADY_APPROVED"
    | "PREVIOUSLY_REVOKED"
    | "LEDGER_CONFLICT"
    | "INACTIVE_LISTING";

export type BrokerOpeningBalanceSkipItem = {
    snapshotItemId: number | null;
    market: string;
    ticker: string;
    displayName: string;
    reason: BrokerOpeningBalanceSkipReason;
}

export type BrokerHoldingImportBatchResponse = {
    snapshotId: number;
    snapshotSyncedAt: string;
    approvedCount: number;
    skippedCount: number;
    approved: PortfolioBrokerHoldingImport[];
    skipped: BrokerOpeningBalanceSkipItem[];
}

export type PortfolioBrokerHoldingAdjustmentStatus = "ACTIVE" | "REVOKED";

export type PortfolioBrokerHoldingAdjustment = {
    id: number;
    snapshotItemId: number | null;
    market: string;
    ticker: string;
    displayName: string;
    deltaQuantity: number;
    unitPrice: number;
    brokerQuantity: number;
    brokerAveragePurchasePrice: number;
    ledgerQuantityBefore: number;
    ledgerAveragePurchasePriceBefore: number;
    snapshotSyncedAt: string;
    tradeTransactionId: number;
    approvedByMemberId: number;
    approvedAt: string;
    status: PortfolioBrokerHoldingAdjustmentStatus;
};
