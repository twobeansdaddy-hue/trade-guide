import type {
    AssetStrategyGuide,
    EmptyHoldingsGuidance,
    UnavailableAsset,
} from "./strategyGuide";

export type PremarketGuideStatus = "NOT_GENERATED" | "COMPLETED" | "PARTIAL";

export type PremarketGuide = {
    snapshotId: number | null;
    guideDate: string;
    generatedAt: string | null;
    status: PremarketGuideStatus;
    heldGuides: AssetStrategyGuide[];
    candidateGuides: AssetStrategyGuide[];
    unavailableAssets: UnavailableAsset[];
    emptyHoldingsGuidance: EmptyHoldingsGuidance | null;
    dataAsOfFrom: string | null;
    dataAsOfTo: string | null;
    availableGuideCount: number;
    unavailableCount: number;
    marketDataProvider: string | null;
    inputEvidenceStatus: "UNVERIFIED" | "CAPTURED" | "VERIFIED" | null;
};
