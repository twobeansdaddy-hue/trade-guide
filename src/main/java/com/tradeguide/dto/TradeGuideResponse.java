package com.tradeguide.dto;

import com.tradeguide.domain.TradeAction;
import com.tradeguide.domain.TradeGuideResult;

public class TradeGuideResponse {
    private final double currentReturnRate;
    private final double targetSellPrice;
    private final double stopLossPrice;
    private final TradeAction tradeAction;
    private final String tradeActionMessage;

    public TradeGuideResponse(
            double currentReturnRate,
            double targetSellPrice,
            double stopLossPrice,
            TradeAction tradeAction,
            String tradeActionMessage
    ) {
        this.currentReturnRate = currentReturnRate;
        this.targetSellPrice = targetSellPrice;
        this.stopLossPrice = stopLossPrice;
        this.tradeAction = tradeAction;
        this.tradeActionMessage = tradeActionMessage;
    }

    public static TradeGuideResponse from(TradeGuideResult result) {
        return new TradeGuideResponse(
                result.getCurrentReturnRate(),
                result.getTargetSellPrice(),
                result.getStopLossPrice(),
                result.getTradeAction(),
                result.getTradeActionMessage()
        );
    }

    public double getCurrentReturnRate() {
        return currentReturnRate;
    }

    public double getTargetSellPrice() {
        return targetSellPrice;
    }

    public double getStopLossPrice() {
        return stopLossPrice;
    }

    public TradeAction getTradeAction() {
        return tradeAction;
    }

    public String getTradeActionMessage() {
        return tradeActionMessage;
    }
}
