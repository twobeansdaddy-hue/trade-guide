package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.PlannedTradeAction;
import com.tradeguide.domain.strategy.PlannedTradeActionType;

import java.math.BigDecimal;

public class PlannedTradeActionResponse {

    private final PlannedTradeActionType type;
    private final BigDecimal triggerPrice;
    private final BigDecimal quantity;
    private final BigDecimal amount;
    private final String reason;
    private final StrategyMetadataResponse strategyMetadata;
    private final boolean requiresUserConfirmation;

    public PlannedTradeActionResponse(
            PlannedTradeActionType type,
            BigDecimal triggerPrice,
            BigDecimal quantity,
            BigDecimal amount,
            String reason,
            StrategyMetadataResponse strategyMetadata,
            boolean requiresUserConfirmation
    ) {
        this.type = type;
        this.triggerPrice = triggerPrice;
        this.quantity = quantity;
        this.amount = amount;
        this.reason = reason;
        this.strategyMetadata = strategyMetadata;
        this.requiresUserConfirmation = requiresUserConfirmation;
    }

    public static PlannedTradeActionResponse from(PlannedTradeAction action) {
        return new PlannedTradeActionResponse(
                action.getType(),
                action.getTriggerPrice(),
                action.getQuantity(),
                action.getAmount(),
                action.getReason(),
                StrategyMetadataResponse.from(action.getStrategyMetadata()),
                action.isRequiresUserConfirmation()
        );
    }

    public PlannedTradeActionType getType() {
        return type;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
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
}
