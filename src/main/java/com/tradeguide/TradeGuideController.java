package com.tradeguide;

import jakarta.validation.Valid;
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
    public TradeGuideResponse calculate(@Valid @RequestBody TradeGuideCalculateRequest request) {
        TradeGuideRequest tradeGuideRequest = request.toTradeGuideRequest();
        TradeGuideResult result = calculator.calculate(tradeGuideRequest);

        return TradeGuideResponse.from(result);
    }
}
