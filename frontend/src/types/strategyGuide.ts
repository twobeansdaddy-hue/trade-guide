export type StrategyAction = "BUY" | "HOLD" | "REDUCE" | "SELL" | "WATCH";

export type StrategyDecision = {
    action: StrategyAction;
    referencePrice: number;
    reason: string;
    metadata: {
        strategyId: string;
        strategyVersion: string;
        dataAsOf: string;
    };
    trend: "ABOVE_LONG_AVERAGE" | "BELOW_LONG_AVERAGE";
    signalEvent: "CROSS_UP" | "CROSS_DOWN" | "NONE";
    weeksSinceCross: number | null;
};

export type AssetStrategyGuide = {
    market: string;
    ticker: string;
    decision: StrategyDecision;
};

export type UnavailableAsset = {
    market: string;
    ticker: string;
    message: string;
};

export type StrategyGuideBatch = {
    guides: AssetStrategyGuide[];
    unavailableAssets: UnavailableAsset[];
};
