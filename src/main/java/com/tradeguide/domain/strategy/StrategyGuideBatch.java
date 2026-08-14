package com.tradeguide.domain.strategy;

import java.util.List;

public class StrategyGuideBatch {

    private final List<AssetStrategyGuide> guides;
    private final List<UnavailableAsset> unavailableAssets;

    public StrategyGuideBatch(
            List<AssetStrategyGuide> guides,
            List<UnavailableAsset> unavailableAssets
    ) {
        this.guides = guides;
        this.unavailableAssets = unavailableAssets;
    }

    public List<AssetStrategyGuide> getGuides() {
        return guides;
    }

    public List<UnavailableAsset> getUnavailableAssets() {
        return unavailableAssets;
    }
}
