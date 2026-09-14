import type {InvestmentTrack} from "./portfolioStrategyProfile";
import type {Market} from "./tradeTransaction";

export type PortfolioCandidateAsset = {
    id: number;
    market: Market;
    ticker: string;
    displayName: string;
    investmentTrack: InvestmentTrack;
    createdAt: string;
};

export type PortfolioCandidateAssetCreateRequest = {
    market: Market;
    ticker: string;
    displayName: string;
};
