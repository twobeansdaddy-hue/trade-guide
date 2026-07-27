package com.tradeguide;

public class Main {
    public static void main(String[] args) {
        // 1. TradeGuideRequest 생성
        TradeGuideRequest request = new TradeGuideRequest(100, 120, 15, 8);
        // 2. TradeGuideCalculator 생성
        TradeGuideCalculator tradeGuideCalculator = new TradeGuideCalculator();
        // 3. calculate() 호출
        TradeGuideResult result = tradeGuideCalculator.calculate(request);
        // 4. TradeGuideResult의 getter로 결과 출력
        System.out.println("현재 수익률 : " + result.getCurrentReturnRate());
        System.out.println("목표 매도가 : " + result.getTargetSellPrice());
        System.out.println("손절 기준가 : " + result.getStopLossPrice());
        System.out.println("액션 : " + result.getTradeAction());
        System.out.println("메시지 : " + result.getTradeActionMessage());

    }
}
