package com.trade_guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TradeGuideCalculatorTest {
    @Test
    void 목표_매도가_이상이면_익절한다() {
        TradeGuideRequest request = new TradeGuideRequest(100, 120, 15, 8);
        TradeGuideCalculator calculator = new TradeGuideCalculator();
        TradeGuideResult result = calculator.calculate(request);
        assertEquals(TradeAction.TAKE_PROFIT, result.getTradeAction());
        assertEquals(20.0, result.getCurrentReturnRate(), 0.001);
        assertEquals(115.0, result.getTargetSellPrice(), 0.001);
        assertEquals(92.0, result.getStopLossPrice(), 0.001);
    }

    @Test
    void 손절가_이하면_매도한다() {
        TradeGuideRequest request = new TradeGuideRequest(100, 90, 15, 8);
        TradeGuideCalculator calculator = new TradeGuideCalculator();
        TradeGuideResult result = calculator.calculate(request);
        assertEquals(TradeAction.SELL, result.getTradeAction());
    }

    @Test
    void 손절가와_목표가_사이면_보유한다() {
        TradeGuideRequest request = new TradeGuideRequest(100, 100, 15, 8);
        TradeGuideCalculator calculator = new TradeGuideCalculator();
        TradeGuideResult result = calculator.calculate(request);
        assertEquals(TradeAction.HOLD, result.getTradeAction());
    }

    @Test
    void 평균_매입가가_0이면_예외가_발생한다() {
        TradeGuideRequest request =
                new TradeGuideRequest(0, 120, 15, 8);

        TradeGuideCalculator calculator =
                new TradeGuideCalculator();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(request)
        );

        assertEquals("평균 매입가는 0보다 커야 합니다.", exception.getMessage());

    }

    @Test
    void 현재가가_0이면_오류가_발생한다() {
        TradeGuideRequest request = new TradeGuideRequest(100, 0, 15, 8);
        TradeGuideCalculator calculator = new TradeGuideCalculator();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(request)
        );

        assertEquals("현재가는 0보다 커야 합니다.", exception.getMessage());
    }

    @Test
    void 목표수익률은_0이상이어야_한다() {
        TradeGuideRequest request = new TradeGuideRequest(100, 120, -10, 8);
        TradeGuideCalculator calculator = new TradeGuideCalculator();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(request)
        );

        assertEquals("목표 수익률은 0 이상이어야 합니다.", exception.getMessage());
    }

    @Test
    void 최대손실률은_0이상이어야_한다() {
        TradeGuideRequest request = new TradeGuideRequest(100, 120, 15, -1);
        TradeGuideCalculator calculator = new TradeGuideCalculator();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(request)
        );

        assertEquals("최대 손실률은 0 이상이어야 합니다.", exception.getMessage());
    }

    @Test
    void 요청이_null이면_오류가_발생한다() {
        TradeGuideCalculator calculator = new TradeGuideCalculator();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(null)
        );

        assertEquals("요청 정보는 필수입니다.", exception.getMessage());
    }
}
