package com.tradeguide.controller.portfolio;

import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.dto.portfolio.PortfolioCreateRequest;
import com.tradeguide.dto.portfolio.PortfolioResponse;
import com.tradeguide.service.portfolio.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members/{memberId}/portfolios")
public class PortfolioController {
    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
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
}
