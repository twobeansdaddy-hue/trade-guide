package com.tradeguide;

import org.springframework.stereotype.Service;

@Service
public class TradeGuideCalculator {

    public TradeGuideResult calculate(TradeGuideRequest request) {

        // 0. 입력값 검증
        validate(request);

        // 1. request의 getter로 입력값 꺼내기
        double currentPrice = request.getCurrentPrice();
        double averagePrice = request.getAveragePrice();
        double targetReturnRate = request.getTargetReturnRate();
        double maximumLossRate = request.getMaximumLossRate();

        // 2. 3가지 값 계산
        // 현재 수익률 = (현재가 - 평균 매입가) / 평균 매입가 × 100
        double currentReturnRate = ((currentPrice - averagePrice) / averagePrice) * 100;
        // 목표 매도가 = 평균 매입가 × (1 + 목표 수익률 / 100)
        double targetSellPrice = averagePrice * (1 + targetReturnRate / 100);
        // 손절 기준가 = 평균 매입가 × (1 - 최대 손실률 / 100)
        double stopLossPrice = averagePrice * (1 - maximumLossRate / 100);

        // 3. tradeAction 정의
        TradeAction tradeAction = determineTradeAction(currentPrice, targetSellPrice, stopLossPrice);

        // 3.1 tradeAtion 메시지 Mapping
        String tradeActionMessage = createTradeActionMessage(tradeAction);

        // 4. 결과 Return
        return new TradeGuideResult(currentReturnRate, targetSellPrice, stopLossPrice, tradeAction, tradeActionMessage);

    }

    private void validate(TradeGuideRequest request) {

        // 입력값 유효성 검증
        if (request == null) {
            throw new IllegalArgumentException("요청 정보는 필수입니다.");
        }

        if (request.getAveragePrice() <= 0) {
            throw new IllegalArgumentException("평균 매입가는 0보다 커야 합니다.");
        }

        if (request.getCurrentPrice() <= 0) {
            throw new IllegalArgumentException("현재가는 0보다 커야 합니다.");
        }

        if (request.getTargetReturnRate() < 0) {
            throw new IllegalArgumentException("목표 수익률은 0 이상이어야 합니다.");
        }

        if (request.getMaximumLossRate() < 0) {
            throw new IllegalArgumentException("최대 손실률은 0 이상이어야 합니다.");
        }

        if (request.getMaximumLossRate() > 100) {
            throw new IllegalArgumentException("최대 손실률은 100 이하이어야 합니다.");
        }
    }

    private TradeAction determineTradeAction(double currentPrice, double targetSellPrice, double stopLossPrice) {
        if (currentPrice >= targetSellPrice) {
            return TradeAction.TAKE_PROFIT;
        } else if (currentPrice <= stopLossPrice) {
            return TradeAction.SELL;
        } else {
            return TradeAction.HOLD;
        }
    }

    private String createTradeActionMessage(TradeAction tradeAction) {
        return switch (tradeAction) {
            case TAKE_PROFIT -> "익절 매도 고려";
            case SELL -> "손절 매도 고려";
            case HOLD -> "보유 유지";
        };
    }

}