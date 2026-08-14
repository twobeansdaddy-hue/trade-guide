package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.StrategyGuideBatch;

import java.util.List;

public class StrategyGuideBatchResponse {

    private final List<AssetStrategyGuideResponse> guides;
    private final List<UnavailableAssetResponse> unavailableAssets;

    public StrategyGuideBatchResponse(
            List<AssetStrategyGuideResponse> guides,
            List<UnavailableAssetResponse> unavailableAssets
    ) {
        this.guides = guides;
        this.unavailableAssets = unavailableAssets;
    }

    public static StrategyGuideBatchResponse from(
            StrategyGuideBatch strategyGuideBatch
    ) {
        return new StrategyGuideBatchResponse(
                strategyGuideBatch.getGuides().stream()
                        .map(AssetStrategyGuideResponse::from)
                        .toList(),
                strategyGuideBatch.getUnavailableAssets().stream()
                        .map(UnavailableAssetResponse::from)
                        .toList()
        );
    }

    public List<AssetStrategyGuideResponse> getGuides() {
        return guides;
    }

    public List<UnavailableAssetResponse> getUnavailableAssets() {
        return unavailableAssets;
    }
}