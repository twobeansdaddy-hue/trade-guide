package com.trade_guide;

public class TradeGuideCalculator {
    private double currentPrice;
    private double averagePrice;
    private double currentReturnRate;
    private double targetSellPrice;
    private double stopLossPrice;
    private TradeAction tradeAction;

    public TradeGuideResult calculate(TradeGuideRequest request) {
        // 1. request의 getter로 입력값 꺼내기
        currentPrice = request.getCurrentPrice();
        averagePrice = request.getAveragePrice();

        // 2. 3가지 값 계산
        // 현재 수익률 = (현재가 - 평균 매입가) / 평균 매입가 × 100
        currentReturnRate = ((currentPrice - averagePrice) / averagePrice) * 100;
        // 목표 매도가 = 평균 매입가 × (1 + 목표 수익률 / 100)
        targetSellPrice = averagePrice * (1 + 15/100);
        // 손절 기준가 = 평균 매입가 × (1 - 최대 손실률 / 100)
        stopLossPrice = averagePrice * (1 - 8/100);

        // 3. tradeAction 정의
        if (currentPrice >= targetSellPrice) {
            tradeAction = TradeAction.TAKE_PROFIT;
        } else if (currentPrice <= stopLossPrice) {
            tradeAction = TradeAction.SELL;
        } else {
            tradeAction = TradeAction.HOLD;
        }

        // 4. 결과 Return
        TradeGuideResult tradeGuideResult = new TradeGuideResult(currentReturnRate, targetSellPrice, stopLossPrice, tradeAction);

        return tradeGuideResult;
    }
}
