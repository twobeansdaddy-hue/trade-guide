package com.tradeguide;

public class TradeGuideResult {
    private final double currentReturnRate;
    private final double targetSellPrice;
    private final double stopLossPrice;
    private final TradeAction tradeAction;
    private final String tradeActionMessage;

    public TradeGuideResult(
            double currentReturnRate,
            double targetSellPrice,
            double stopLossPrice,
            TradeAction tradeAction,
            String tradeActionMessage) {
        this.currentReturnRate = currentReturnRate;
        this.targetSellPrice = targetSellPrice;
        this.stopLossPrice = stopLossPrice;
        this.tradeAction = tradeAction;
        this.tradeActionMessage = tradeActionMessage;
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
