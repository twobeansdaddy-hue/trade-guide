package com.tradeguide.domain.strategy;

import java.util.List;

public class StrategyGuideBatch {

    private final List<AssetStrategyGuide> guides;
    private final List<UnavailableAsset> unavailableAssets;
    private final EmptyHoldingsGuidance emptyHoldingsGuidance;

    public StrategyGuideBatch(
            List<AssetStrategyGuide> guides,
            List<UnavailableAsset> unavailableAssets
    ) {
        this(guides, unavailableAssets, null);
    }

    public StrategyGuideBatch(
            List<AssetStrategyGuide> guides,
            List<UnavailableAsset> unavailableAssets,
            EmptyHoldingsGuidance emptyHoldingsGuidance
    ) {
        this.guides = guides;
        this.unavailableAssets = unavailableAssets;
        this.emptyHoldingsGuidance = emptyHoldingsGuidance;
    }

    public List<AssetStrategyGuide> getGuides() {
        return guides;
    }

    public List<UnavailableAsset> getUnavailableAssets() {
        return unavailableAssets;
    }

    public EmptyHoldingsGuidance getEmptyHoldingsGuidance() {
        return emptyHoldingsGuidance;
    }
}
