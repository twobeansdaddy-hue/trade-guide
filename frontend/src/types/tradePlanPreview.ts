export type TradePlanPreviewStatus =
    | "BUY"
    | "STOP_LOSS_EXIT_REVIEW"
    | "SELL_REVIEW"
    | "HOLD"
    | "POSSIBLE_ADD_REVIEW"
    | "WATCH"
    | "NOT_READY";

export type TradePlanPreviewNotReadyReason =
    | "MISSING_RISK_POLICY"
    | "MISSING_STOP_LOSS";

export type TradePlanPreviewConstraint =
    | "AVAILABLE_CASH_NOT_SYNCED"
    | "SINGLE_ASSET_EXPOSURE_CAP_APPLIED";

export type PlannedTradeActionType =
    | "PROTECTIVE_EXIT_REVIEW"
    | "POSSIBLE_ADD_REVIEW"
    | "PARTIAL_PROFIT_REVIEW";

export type PlannedTradeAction = {
    type: PlannedTradeActionType;
    triggerPrice: number | null;
    quantity: number | null;
    amount: number | null;
    reason: string;
    strategyMetadata: {
        strategyId: string;
        strategyVersion: string;
        dataAsOf: string;
        confidence?: string | null;
        caveats?: string[];
    };
    requiresUserConfirmation?: boolean;
};

export type AssetTradePlanPreview = {
    market: string;
    ticker: string;
    displayName?: string;
    currency: string;
    status: TradePlanPreviewStatus;
    notReadyReason: TradePlanPreviewNotReadyReason | null;
    referencePrice: number;
    stopLossPrice: number | null;
    quantity: number | null;
    amount: number | null;
    estimatedMaxLoss: number | null;
    constraints: TradePlanPreviewConstraint[];
    reason: string;
    strategyMetadata: {
        strategyId: string;
        strategyVersion: string;
        dataAsOf: string;
        confidence?: string | null;
        caveats?: string[];
    };
    requiresUserConfirmation: boolean;
    plannedActions?: PlannedTradeAction[];
};

export type UnavailableAssetPreview = {
    market: string;
    ticker: string;
    message: string;
    reason: string | null;
};

export type TradePlanPreviewBatch = {
    candidatePlans: AssetTradePlanPreview[];
    heldAssetPlans: AssetTradePlanPreview[];
    unavailableAssets: UnavailableAssetPreview[];
};
