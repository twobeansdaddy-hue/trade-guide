export type PortfolioAssetRiskOverride = {
    market: string;
    ticker: string;
    stopLossRatio: number;
    updatedAt: string | null;
};

export type PortfolioAssetRiskOverrideUpsertRequest = {
    stopLossRatio: number;
};
