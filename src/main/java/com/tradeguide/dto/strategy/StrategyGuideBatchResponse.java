package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.service.asset.AssetDisplayNameResolver;

import java.util.List;

public class StrategyGuideBatchResponse {

    private final List<AssetStrategyGuideResponse> guides;
    private final List<UnavailableAssetResponse> unavailableAssets;
    private final EmptyHoldingsGuidanceResponse emptyHoldingsGuidance;

    public StrategyGuideBatchResponse(
            List<AssetStrategyGuideResponse> guides,
            List<UnavailableAssetResponse> unavailableAssets
    ) {
        this(guides, unavailableAssets, null);
    }

    public StrategyGuideBatchResponse(
            List<AssetStrategyGuideResponse> guides,
            List<UnavailableAssetResponse> unavailableAssets,
            EmptyHoldingsGuidanceResponse emptyHoldingsGuidance
    ) {
        this.guides = guides;
        this.unavailableAssets = unavailableAssets;
        this.emptyHoldingsGuidance = emptyHoldingsGuidance;
    }

    public static StrategyGuideBatchResponse from(
            StrategyGuideBatch strategyGuideBatch,
            AssetDisplayNameResolver displayNameResolver
    ) {
        return new StrategyGuideBatchResponse(
                strategyGuideBatch.getGuides().stream()
                        .map(guide -> AssetStrategyGuideResponse.from(
                                guide,
                                displayNameResolver.resolve(guide.getMarket(), guide.getTicker())
                        ))
                        .toList(),
                strategyGuideBatch.getUnavailableAssets().stream()
                        .map(UnavailableAssetResponse::from)
                        .toList(),
                EmptyHoldingsGuidanceResponse.from(
                        strategyGuideBatch.getEmptyHoldingsGuidance()
                )
        );
    }

    public List<AssetStrategyGuideResponse> getGuides() {
        return guides;
    }

    public List<UnavailableAssetResponse> getUnavailableAssets() {
        return unavailableAssets;
    }

    public EmptyHoldingsGuidanceResponse getEmptyHoldingsGuidance() {
        return emptyHoldingsGuidance;
    }
}