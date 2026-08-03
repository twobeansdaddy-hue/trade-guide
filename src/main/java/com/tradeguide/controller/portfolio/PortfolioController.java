package com.tradeguide.controller.portfolio;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.dto.holding.HoldingResponse;
import com.tradeguide.dto.portfolio.PortfolioCreateRequest;
import com.tradeguide.dto.portfolio.PortfolioResponse;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.portfolio.PortfolioService;
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

    public PortfolioController(
            PortfolioService portfolioService,
            HoldingService holdingService) {
        this.portfolioService = portfolioService;
        this.holdingService = holdingService;
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
}
