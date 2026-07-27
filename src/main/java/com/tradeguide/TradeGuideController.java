package com.tradeguide;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade-guide")
public class TradeGuideController {

    private final TradeGuideCalculator calculator;

    public TradeGuideController(TradeGuideCalculator calculator) {
        this.calculator = calculator;
    }

    @PostMapping("/calculate")
    public TradeGuideResult calculate(@RequestBody TradeGuideRequest request) {
        return calculator.calculate(request);
    }
}
