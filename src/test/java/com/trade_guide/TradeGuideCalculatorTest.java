package com.trade_guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TradeGuideCalculatorTest {
    @Test
    void 목표_매도가_이상이면_익절한다() {
        TradeGuideRequest request = new TradeGuideRequest(100, 120, 15, 8);
        TradeGuideCalculator calculator = new TradeGuideCalculator();
        TradeGuideResult result = calculator.calculate(request);
        assertEquals(TradeAction.TAKE_PROFIT, result.getTradeAction());
    }

    @Test
    void 목표_매도가_이하면_매도한다() {
        TradeGuideRequest request = new TradeGuideRequest(100, 90, 15, 8);
        TradeGuideCalculator calculator = new TradeGuideCalculator();
        TradeGuideResult result = calculator.calculate(request);
        assertEquals(TradeAction.SELL, result.getTradeAction());
    }

    @Test
    void 목표_매도가_사이면_보유한다() {
        TradeGuideRequest request = new TradeGuideRequest(100, 100, 15, 8);
        TradeGuideCalculator calculator = new TradeGuideCalculator();
        TradeGuideResult result = calculator.calculate(request);
        assertEquals(TradeAction.HOLD, result.getTradeAction());
    }
}
