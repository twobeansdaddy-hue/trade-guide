package com.tradeguide.controller.portfolio;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.domain.risk.PortfolioRiskAlert;
import com.tradeguide.dto.holding.HoldingResponse;
import com.tradeguide.dto.portfolio.PortfolioCreateRequest;
import com.tradeguide.dto.portfolio.PortfolioResponse;
import com.tradeguide.dto.valuation.PortfolioValuationResponse;
import com.tradeguide.dto.strategy.StrategyGuideBatchResponse;
import com.tradeguide.dto.risk.HoldingExposureResponse;
import com.tradeguide.dto.risk.PortfolioRiskPolicyResponse;
import com.tradeguide.dto.risk.PortfolioRiskPolicyUpdateRequest;
import com.tradeguide.dto.risk.PortfolioRiskAlertResponse;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.portfolio.PortfolioService;
import com.tradeguide.service.strategy.PortfolioStrategyGuideService;
import com.tradeguide.service.valuation.PortfolioValuationService;
import com.tradeguide.service.risk.PortfolioExposureService;
import com.tradeguide.service.strategy.PortfolioCandidateStrategyGuideService;
import com.tradeguide.service.risk.PortfolioRiskAlertService;

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
    private final PortfolioExposureService portfolioExposureService;
    private final PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService;
    private final PortfolioRiskAlertService portfolioRiskAlertService;

    public PortfolioController(
            PortfolioService portfolioService,
            HoldingService holdingService,
            PortfolioValuationService portfolioValuationService,
            PortfolioStrategyGuideService portfolioStrategyGuideService,
            PortfolioExposureService portfolioExposureService,
            PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService,
            PortfolioRiskAlertService portfolioRiskAlertService
    ) {
        this.portfolioService = portfolioService;
        this.holdingService = holdingService;
        this.portfolioValuationService = portfolioValuationService;
        this.portfolioStrategyGuideService = portfolioStrategyGuideService;
        this.portfolioExposureService = portfolioExposureService;
        this.portfolioCandidateStrategyGuideService = portfolioCandidateStrategyGuideService;
        this.portfolioRiskAlertService = portfolioRiskAlertService;
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

    @PutMapping("/{portfolioId}/risk-policy")
    public PortfolioRiskPolicyResponse updateRiskPolicy(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @Valid @RequestBody PortfolioRiskPolicyUpdateRequest request
    ) {
        PortfolioRiskPolicy riskPolicy = portfolioService.updateRiskPolicy(
                memberId,
                portfolioId,
                request.getMaxLossPerTradeRatio(),
                request.getMaxSingleAssetExposureRatio()
        );

        return PortfolioRiskPolicyResponse.from(riskPolicy);
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

    @GetMapping("/{portfolioId}/strategy-guides")
    public StrategyGuideBatchResponse getPortfolioStrategyGuides(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        StrategyGuideBatch strategyGuideBatch =
                portfolioStrategyGuideService.getPortfolioStrategyGuides(
                        memberId,
                        portfolioId
                );

        return StrategyGuideBatchResponse.from(strategyGuideBatch);
    }

    @GetMapping("/{portfolioId}/exposures")
    public List<HoldingExposureResponse> getPortfolioExposures(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return portfolioExposureService.getExposures(memberId, portfolioId)
                .stream()
                .map(HoldingExposureResponse::from)
                .toList();
    }

    @GetMapping("/{portfolioId}/candidate-strategy-guides")
    public StrategyGuideBatchResponse getCandidateStrategyGuides(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        StrategyGuideBatch strategyGuideBatch =
                portfolioCandidateStrategyGuideService
                        .getCandidateStrategyGuides(memberId, portfolioId);

        return StrategyGuideBatchResponse.from(strategyGuideBatch);
    }

    @GetMapping("/{portfolioId}/risk-policy")
    public PortfolioRiskPolicyResponse getRiskPolicy(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        PortfolioRiskPolicy riskPolicy = portfolioService.getRiskPolicy(memberId, portfolioId);

        return PortfolioRiskPolicyResponse.from(riskPolicy);
    }

    @GetMapping("/{portfolioId}/risk-alerts")
    public List<PortfolioRiskAlertResponse> getPortfolioRiskAlerts(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return portfolioRiskAlertService.getRiskAlerts(memberId, portfolioId)
                .stream()
                .map(PortfolioRiskAlertResponse::from)
                .toList();
    }

}
