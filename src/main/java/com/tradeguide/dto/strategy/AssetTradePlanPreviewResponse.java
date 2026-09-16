package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.AssetTradePlanPreview;
import com.tradeguide.domain.strategy.TradePlanPreviewConstraint;
import com.tradeguide.domain.strategy.TradePlanPreviewNotReadyReason;
import com.tradeguide.domain.strategy.TradePlanPreviewStatus;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.util.List;

public class AssetTradePlanPreviewResponse {

    private final Market market;
    private final String ticker;
    private final String currency;
    private final TradePlanPreviewStatus status;
    private final TradePlanPreviewNotReadyReason notReadyReason;
    private final BigDecimal referencePrice;
    private final BigDecimal stopLossPrice;
    private final BigDecimal quantity;
    private final BigDecimal amount;
    private final BigDecimal estimatedMaxLoss;
    private final List<TradePlanPreviewConstraint> constraints;
    private final String reason;
    private final StrategyMetadataResponse strategyMetadata;
    private final boolean requiresUserConfirmation;
    private final List<PlannedTradeActionResponse> plannedActions;

    public AssetTradePlanPreviewResponse(
            Market market,
            String ticker,
            String currency,
            TradePlanPreviewStatus status,
            TradePlanPreviewNotReadyReason notReadyReason,
            BigDecimal referencePrice,
            BigDecimal stopLossPrice,
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal estimatedMaxLoss,
            List<TradePlanPreviewConstraint> constraints,
            String reason,
            StrategyMetadataResponse strategyMetadata,
            boolean requiresUserConfirmation,
            List<PlannedTradeActionResponse> plannedActions
    ) {
        this.market = market;
        this.ticker = ticker;
        this.currency = currency;
        this.status = status;
        this.notReadyReason = notReadyReason;
        this.referencePrice = referencePrice;
        this.stopLossPrice = stopLossPrice;
        this.quantity = quantity;
        this.amount = amount;
        this.estimatedMaxLoss = estimatedMaxLoss;
        this.constraints = constraints;
        this.reason = reason;
        this.strategyMetadata = strategyMetadata;
        this.requiresUserConfirmation = requiresUserConfirmation;
        this.plannedActions = plannedActions;
    }

    public static AssetTradePlanPreviewResponse from(AssetTradePlanPreview preview) {
        return new AssetTradePlanPreviewResponse(
                preview.getMarket(),
                preview.getTicker(),
                resolveCurrency(preview.getMarket()),
                preview.getStatus(),
                preview.getNotReadyReason(),
                preview.getReferencePrice(),
                preview.getStopLossPrice(),
                preview.getQuantity(),
                preview.getAmount(),
                preview.getEstimatedMaxLoss(),
                preview.getConstraints(),
                preview.getReason(),
                StrategyMetadataResponse.from(preview.getStrategyMetadata()),
                preview.isRequiresUserConfirmation(),
                preview.getPlannedActions().stream()
                        .map(PlannedTradeActionResponse::from)
                        .toList()
        );
    }

    private static String resolveCurrency(Market market) {
        return market == Market.US ? "USD" : "KRW";
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public String getCurrency() {
        return currency;
    }

    public TradePlanPreviewStatus getStatus() {
        return status;
    }

    public TradePlanPreviewNotReadyReason getNotReadyReason() {
        return notReadyReason;
    }

    public BigDecimal getReferencePrice() {
        return referencePrice;
    }

    public BigDecimal getStopLossPrice() {
        return stopLossPrice;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getEstimatedMaxLoss() {
        return estimatedMaxLoss;
    }

    public List<TradePlanPreviewConstraint> getConstraints() {
        return constraints;
    }

    public String getReason() {
        return reason;
    }

    public StrategyMetadataResponse getStrategyMetadata() {
        return strategyMetadata;
    }

    public boolean isRequiresUserConfirmation() {
        return requiresUserConfirmation;
    }

    public List<PlannedTradeActionResponse> getPlannedActions() {
        return plannedActions;
    }
}
