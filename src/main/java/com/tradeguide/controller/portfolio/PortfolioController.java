package com.tradeguide.controller.portfolio;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.dto.holding.HoldingResponse;
import com.tradeguide.dto.portfolio.PortfolioCreateRequest;
import com.tradeguide.dto.portfolio.PortfolioResponse;
import com.tradeguide.dto.strategy.AssetStrategyGuideResponse;
import com.tradeguide.dto.valuation.PortfolioValuationResponse;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.portfolio.PortfolioService;
import com.tradeguide.service.strategy.PortfolioStrategyGuideService;
import com.tradeguide.service.valuation.PortfolioValuationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members/{memberId}/portfolios")
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final HoldingService holdingService;
    private final PortfolioValuationService portfolioValuationService;
    private final PortfolioStrategyGuideService portfolioStrategyGuideService;

    public PortfolioController(
            PortfolioService portfolioService,
            HoldingService holdingService,
            PortfolioValuationService portfolioValuationService,
            PortfolioStrategyGuideService portfolioStrategyGuideService) {
        this.portfolioService = portfolioService;
        this.holdingService = holdingService;
        this.portfolioValuationService = portfolioValuationService;
        this.portfolioStrategyGuideService = portfolioStrategyGuideService;
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> createPortfolio(
            @PathVariable Long memberId,
            @Valid @RequestBody PortfolioCreateRequest request
            ) {
        Portfolio portfolio = portfolioService.createPortfolio(memberId, request.getName());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PortfolioResponse.from(portfolio));
    }

    @GetMapping("/{portfolioId}/holdings")
    public List<HoldingResponse> getHoldings(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        List<Holding> holdings = holdingService.getHoldings(memberId, portfolioId);

        return holdings.stream()
                .map(HoldingResponse::from)
                .toList();
    }

    @GetMapping("/{portfolioId}/valuation")
    public PortfolioValuationResponse getPortfolioValuation(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        PortfolioValuation valuation = portfolioValuationService.getPortfolioValuation(
                memberId,
                portfolioId
        );

        return PortfolioValuationResponse.from(valuation);
    }

    @GetMapping("/{portfoiloId}/strategy-guides")
    public List<AssetStrategyGuideResponse> getPortfolioStrategyGuides(
            @PathVariable Long memberId,
            @PathVariable Long portfoiloId
    ) {

        List<AssetStrategyGuide> strategyGuides = portfolioStrategyGuideService.getPortfolioStrategyGuides(
                memberId,
                portfoiloId
        );

        return strategyGuides.stream()
                .map(AssetStrategyGuideResponse::from)
                .toList();
    }
}
