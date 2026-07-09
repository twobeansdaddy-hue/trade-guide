package com.trade_guide;

public class TradeGuideCalculator {

    public TradeGuideResult calculate(TradeGuideRequest request) {
        // 1. request의 getter로 입력값 꺼내기
        double currentPrice = request.getCurrentPrice();
        double averagePrice = request.getAveragePrice();
        double targetReturnRate = request.getTargetReturnRate();
        double maximumLossRate = request.getMaximumLossRate();

        // 입력값 유효성 검증
        if (request.getAveragePrice() <= 0) {
            throw new IllegalArgumentException("평균 매입가는 0보다 커야 합니다.");
        }

        // 입력값 유효성 검증
        if (request.getCurrentPrice() <= 0) {
            throw new IllegalArgumentException("현재가는 0 이상이어야 합니다.");
        }

        // 2. 3가지 값 계산
        // 현재 수익률 = (현재가 - 평균 매입가) / 평균 매입가 × 100
        double currentReturnRate = ((currentPrice - averagePrice) / averagePrice) * 100;
        // 목표 매도가 = 평균 매입가 × (1 + 목표 수익률 / 100)
        double targetSellPrice = averagePrice * (1 + targetReturnRate / 100);
        // 손절 기준가 = 평균 매입가 × (1 - 최대 손실률 / 100)
        double stopLossPrice = averagePrice * (1 - maximumLossRate / 100);

        // 3. tradeAction 정의
        TradeAction tradeAction;
        if (currentPrice >= targetSellPrice) {
            tradeAction = TradeAction.TAKE_PROFIT;
        } else if (currentPrice <= stopLossPrice) {
            tradeAction = TradeAction.SELL;
        } else {
            tradeAction = TradeAction.HOLD;
        }

        // 4. 결과 Return
        return new TradeGuideResult(currentReturnRate, targetSellPrice, stopLossPrice, tradeAction);
    }
}