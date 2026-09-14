export type InvestmentTrack = "TRACK_A" | "TRACK_B";

export type PortfolioAssetStrategyProfile = {
    market: string;
    ticker: string;
    overrideTrack: InvestmentTrack | null;
    globalTrack: InvestmentTrack | null;
    effectiveTrack: InvestmentTrack | null;
    updatedAt: string | null;
};

export type PortfolioAssetStrategyProfileUpsertRequest = {
    investmentTrack: InvestmentTrack;
};
