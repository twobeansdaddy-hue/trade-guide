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
}
