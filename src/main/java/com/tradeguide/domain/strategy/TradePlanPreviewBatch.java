package com.tradeguide.domain.strategy;

import java.util.List;

/**
 * 한 포트폴리오의 검토용 매매 계획 미리보기 묶음이다. 매수 후보와 보유 종목을 분리해
 * 담고, 신호를 계산하지 못한 종목은 기존 {@link UnavailableAsset}로 그대로 전달한다.
 */
public class TradePlanPreviewBatch {

    private final List<AssetTradePlanPreview> candidatePlans;
    private final List<AssetTradePlanPreview> heldAssetPlans;
    private final List<UnavailableAsset> unavailableAssets;

    public TradePlanPreviewBatch(
            List<AssetTradePlanPreview> candidatePlans,
            List<AssetTradePlanPreview> heldAssetPlans,
            List<UnavailableAsset> unavailableAssets
    ) {
        this.candidatePlans = candidatePlans == null ? List.of() : List.copyOf(candidatePlans);
        this.heldAssetPlans = heldAssetPlans == null ? List.of() : List.copyOf(heldAssetPlans);
        this.unavailableAssets = unavailableAssets == null ? List.of() : List.copyOf(unavailableAssets);
    }

    public List<AssetTradePlanPreview> getCandidatePlans() {
        return candidatePlans;
    }

    public List<AssetTradePlanPreview> getHeldAssetPlans() {
        return heldAssetPlans;
    }

    public List<UnavailableAsset> getUnavailableAssets() {
        return unavailableAssets;
    }
}
