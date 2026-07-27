package com.trade_guide;

public class TradeGuideRequest {
    private final double averagePrice;
    private final double currentPrice;
    private final double targetReturnRate;
    private final double maximumLossRate;

    public TradeGuideRequest(double averagePrice, double currentPrice, double targetReturnRate, double maximumLossRate) {
        this.averagePrice = averagePrice;
        this.currentPrice = currentPrice;
        this.targetReturnRate = targetReturnRate;
        this.maximumLossRate = maximumLossRate;
    }

    public double getAveragePrice() {
        return averagePrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getTargetReturnRate() {
        return targetReturnRate;
    }

    public double getMaximumLossRate() {
        return maximumLossRate;
    }
}
