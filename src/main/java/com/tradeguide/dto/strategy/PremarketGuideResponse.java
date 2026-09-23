package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.PremarketGuideItem;
import com.tradeguide.domain.strategy.PremarketGuideItemStatus;
import com.tradeguide.domain.strategy.PremarketGuideScope;
import com.tradeguide.domain.strategy.PremarketGuideSnapshot;
import com.tradeguide.domain.strategy.PremarketGuideStatus;
import com.tradeguide.domain.strategy.StrategyDecisionGuidance;
import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.service.asset.AssetDisplayNameResolver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PremarketGuideResponse(
        Long snapshotId,
        LocalDate guideDate,
        LocalDateTime generatedAt,
        PremarketGuideStatus status,
        List<AssetStrategyGuideResponse> heldGuides,
        List<AssetStrategyGuideResponse> candidateGuides,
        List<UnavailableAssetResponse> unavailableAssets,
        EmptyHoldingsGuidanceResponse emptyHoldingsGuidance,
        LocalDate dataAsOfFrom,
        LocalDate dataAsOfTo,
        int availableGuideCount,
        int unavailableCount,
        String marketDataProvider,
        String inputEvidenceStatus
) {
    public static PremarketGuideResponse notGenerated(LocalDate guideDate) {
        return new PremarketGuideResponse(
                null,
                guideDate,
                null,
                PremarketGuideStatus.NOT_GENERATED,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                0,
                0,
                null,
                null
        );
    }

    public static PremarketGuideResponse from(
            PremarketGuideSnapshot snapshot,
            AssetDisplayNameResolver displayNameResolver
    ) {
        List<PremarketGuideItem> items = snapshot.getItems();
        List<PremarketGuideItem> availableItems = items.stream()
                .filter(item -> item.getStatus() == PremarketGuideItemStatus.AVAILABLE)
                .toList();

        List<AssetStrategyGuideResponse> heldGuides = availableItems.stream()
                .filter(item -> item.getScope() == PremarketGuideScope.HELD)
                .map(item -> toGuideResponse(item, displayNameResolver))
                .toList();
        List<AssetStrategyGuideResponse> candidateGuides = availableItems.stream()
                .filter(item -> item.getScope() == PremarketGuideScope.CANDIDATE)
                .map(item -> toGuideResponse(item, displayNameResolver))
                .toList();
        List<UnavailableAssetResponse> unavailableAssets = items.stream()
                .filter(item -> item.getStatus() == PremarketGuideItemStatus.UNAVAILABLE)
                .map(item -> new UnavailableAssetResponse(
                        item.getMarket(),
                        item.getTicker(),
                        item.getReason(),
                        item.getUnavailableReason() == null
                                ? null
                                : item.getUnavailableReason().name()
                ))
                .toList();

        List<LocalDate> dataAsOfDates = availableItems.stream()
                .map(PremarketGuideItem::getDataAsOf)
                .filter(date -> date != null)
                .sorted()
                .toList();

        EmptyHoldingsGuidanceResponse emptyGuidance = snapshot.getHeldEmptyReason() == null
                ? null
                : new EmptyHoldingsGuidanceResponse(
                        snapshot.getHeldEmptyReason(),
                        snapshot.getHeldEmptyMessage()
                );

        return new PremarketGuideResponse(
                snapshot.getId(),
                snapshot.getGuideDate(),
                snapshot.getGeneratedAt(),
                snapshot.getStatus(),
                heldGuides,
                candidateGuides,
                unavailableAssets,
                emptyGuidance,
                dataAsOfDates.isEmpty() ? null : dataAsOfDates.get(0),
                dataAsOfDates.isEmpty() ? null : dataAsOfDates.get(dataAsOfDates.size() - 1),
                availableItems.size(),
                unavailableAssets.size(),
                snapshot.getMarketDataProvider() == null
                        ? null
                        : snapshot.getMarketDataProvider().name(),
                snapshot.getInputAudit() == null
                        ? null
                        : snapshot.getInputAudit().getEvidenceStatus().name()
        );
    }

    private static AssetStrategyGuideResponse toGuideResponse(
            PremarketGuideItem item,
            AssetDisplayNameResolver displayNameResolver
    ) {
        StrategyMetadata metadata = new StrategyMetadata(
                item.getStrategyId(),
                item.getStrategyVersion(),
                item.getDataAsOf(),
                item.getConfidence(),
                item.getCaveats()
        );
        StrategySignal signal = new StrategySignal(
                item.getReferencePrice(),
                item.getReason(),
                metadata,
                item.getTrend(),
                item.getSignalEvent(),
                item.getWeeksSinceCross()
        );
        StrategyDecisionGuidance guidance = new StrategyDecisionGuidance(
                item.getEntryTimingStatus(),
                item.getEntryTimingMessage(),
                item.getStopLossStatus(),
                item.getStopLossRatio(),
                item.getStopLossPrice(),
                item.getStopLossMessage()
        );

        return new AssetStrategyGuideResponse(
                item.getMarket(),
                item.getTicker(),
                displayNameResolver.resolve(item.getMarket(), item.getTicker()),
                new StrategyDecisionResponse(
                        item.getAction(),
                        item.getReferencePrice(),
                        item.getReason(),
                        StrategyMetadataResponse.from(metadata),
                        item.getTrend(),
                        item.getSignalEvent(),
                        item.getWeeksSinceCross(),
                        StrategyDecisionGuidanceResponse.from(guidance)
                )
        );
    }
}
