package com.tradeguide;

public class TradeGuideCalculateRequest {
    private final double averagePrice;
    private final double currentPrice;
    private final double targetReturnRate;
    private final double maximumLossRate;

    public TradeGuideCalculateRequest(
            double averagePrice,
            double currentPrice,
            double targetReturnRate,
            double maximumLossRate
    ) {
        this.averagePrice = averagePrice;
        this.currentPrice = currentPrice;
        this.targetReturnRate = targetReturnRate;
        this.maximumLossRate = maximumLossRate;
    }

    public TradeGuideRequest toTradeGuideRequest() {
        return new TradeGuideRequest(
                averagePrice,
                currentPrice,
                targetReturnRate,
                maximumLossRate
        );
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
