package com.tradeguide.controller.strategy;

import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.strategy.StrategyDecisionResponse;
import com.tradeguide.dto.strategy.StrategySignalResponse;
import com.tradeguide.service.strategy.StrategyGuideService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/markets/{market}/stocks/{ticker}/strategy-guide")
public class StrategyGuideController {

    private final StrategyGuideService strategyGuideService;

    public StrategyGuideController(StrategyGuideService strategyGuideService) {
        this.strategyGuideService = strategyGuideService;
    }

    @GetMapping
    public StrategySignalResponse getStrategyGuide(
            @PathVariable Market market,
            @PathVariable String ticker
    ) {
        StrategySignal signal = strategyGuideService.getStrategySignal(market, ticker);

        return StrategySignalResponse.from(signal);
    }
}
