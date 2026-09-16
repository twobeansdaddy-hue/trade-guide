export type StrategyAction = "BUY" | "HOLD" | "REDUCE" | "SELL" | "WATCH";

export type StrategyDecision = {
    action: StrategyAction;
    referencePrice: number;
    reason: string;
    metadata: {
        strategyId: string;
        strategyVersion: string;
        dataAsOf: string;
        confidence?: string | null;
        caveats?: string[];
    };
    trend: "ABOVE_LONG_AVERAGE" | "BELOW_LONG_AVERAGE" | null;
    signalEvent: "CROSS_UP" | "CROSS_DOWN" | "NONE" | null;
    weeksSinceCross: number | null;
    guidance?: {
        entryTimingStatus: string;
        entryTimingMessage: string;
        stopLossStatus: string;
        stopLossRatio: number | null;
        stopLossPrice: number | null;
        stopLossMessage: string;
    };
};

export type AssetStrategyGuide = {
    market: string;
    ticker: string;
    displayName?: string;
    decision: StrategyDecision;
};

export type StrategyGuideUnavailableReason =
    | "ASSET_PROFILE_NOT_FOUND"
    | "MARKET_DATA_UNAVAILABLE"
    | "MARKET_DATA_RATE_LIMIT_EXCEEDED";

export type UnavailableAsset = {
    market: string;
    ticker: string;
    message: string;
    reason?: StrategyGuideUnavailableReason | string | null;
};

export type EmptyHoldingsReason =
    | "NO_BROKER_SNAPSHOT"
    | "BROKER_SNAPSHOT_NOT_REFLECTED";

export type EmptyHoldingsGuidance = {
    reason: EmptyHoldingsReason | string;
    message: string;
};

export type StrategyGuideBatch = {
    guides: AssetStrategyGuide[];
    unavailableAssets: UnavailableAsset[];
    emptyHoldingsGuidance?: EmptyHoldingsGuidance | null;
};
