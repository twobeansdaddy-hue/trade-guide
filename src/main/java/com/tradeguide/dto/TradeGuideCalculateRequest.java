package com.tradeguide.dto;

import com.tradeguide.domain.TradeGuideRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class TradeGuideCalculateRequest {
    @Positive(message = "평균 매입가는 0보다 커야 합니다.")
    private final double averagePrice;
    @Positive(message = "현재가는 0보다 커야 합니다.")
    private final double currentPrice;
    @PositiveOrZero(message = "목표 수익률은 0 이상이어야 합니다.")
    private final double targetReturnRate;
    @PositiveOrZero(message = "최대 손실률은 0 이상이어야 합니다.")
    @Max(value = 100, message = "최대 손실률은 100 이하이어야 합니다.")
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
