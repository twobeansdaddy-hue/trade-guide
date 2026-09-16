package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.TradePlanPreviewBatch;

import java.util.List;

public class TradePlanPreviewBatchResponse {

    private final List<AssetTradePlanPreviewResponse> candidatePlans;
    private final List<AssetTradePlanPreviewResponse> heldAssetPlans;
    private final List<UnavailableAssetResponse> unavailableAssets;

    public TradePlanPreviewBatchResponse(
            List<AssetTradePlanPreviewResponse> candidatePlans,
            List<AssetTradePlanPreviewResponse> heldAssetPlans,
            List<UnavailableAssetResponse> unavailableAssets
    ) {
        this.candidatePlans = candidatePlans;
        this.heldAssetPlans = heldAssetPlans;
        this.unavailableAssets = unavailableAssets;
    }

    public static TradePlanPreviewBatchResponse from(TradePlanPreviewBatch batch) {
        return new TradePlanPreviewBatchResponse(
                batch.getCandidatePlans().stream()
                        .map(AssetTradePlanPreviewResponse::from)
                        .toList(),
                batch.getHeldAssetPlans().stream()
                        .map(AssetTradePlanPreviewResponse::from)
                        .toList(),
                batch.getUnavailableAssets().stream()
                        .map(UnavailableAssetResponse::from)
                        .toList()
        );
    }

    public List<AssetTradePlanPreviewResponse> getCandidatePlans() {
        return candidatePlans;
    }

    public List<AssetTradePlanPreviewResponse> getHeldAssetPlans() {
        return heldAssetPlans;
    }

    public List<UnavailableAssetResponse> getUnavailableAssets() {
        return unavailableAssets;
    }
}
