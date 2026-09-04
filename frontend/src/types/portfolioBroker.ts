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
    brokerQuantity: number;
    brokerAveragePurchasePrice: number;
    tradeGuideQuantity: number;
    comparison: BrokerHoldingComparison;
}

export type BrokerHoldingPreview = {
    provider: string;
    brokerConnectionId: number;
    maskedAccountNumber: string;
    syncedAt: string;
    items: BrokerHoldingPreviewItem[];
    unsupportedMarketCount: number;
}
