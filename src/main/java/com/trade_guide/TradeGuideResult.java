package com.trade_guide;

public class TradeGuideResult {
    private double currentReturnRate;
    private double targetSellPrice;
    private double stopLossPrice;
    private TradeAction tradeAction;

    public TradeGuideResult(double currentReturnRate, double targetSellPrice, double stopLossPrice, TradeAction tradeAction) {
        this.currentReturnRate = currentReturnRate;
        this.targetSellPrice = targetSellPrice;
        this.stopLossPrice = stopLossPrice;
        this.tradeAction = tradeAction;
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
}
