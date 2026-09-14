package com.tradeguide.controller.strategy;

import com.tradeguide.domain.strategy.PortfolioCandidateAsset;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.strategy.PortfolioCandidateAssetCreateRequest;
import com.tradeguide.dto.strategy.PortfolioCandidateAssetResponse;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.strategy.PortfolioCandidateAssetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 포트폴리오별 Track A 후보 종목 등록·조회·삭제 API다. 등록된 후보는 이 포트폴리오의
 * 후보 가이드 조회({@code GET .../candidate-strategy-guides})가 전역
 * {@code /api/admin/asset-profiles} 카탈로그보다 우선 사용한다. 다른 포트폴리오나 전역
 * 카탈로그는 바꾸지 않는다.
 */
@RestController
@RequestMapping("/api/members/{memberId}/portfolios/{portfolioId}/candidate-assets")
public class PortfolioCandidateAssetController {

    private final PortfolioCandidateAssetService portfolioCandidateAssetService;
    private final MemberAccessService memberAccessService;

    public PortfolioCandidateAssetController(
            PortfolioCandidateAssetService portfolioCandidateAssetService,
            MemberAccessService memberAccessService
    ) {
        this.portfolioCandidateAssetService = portfolioCandidateAssetService;
        this.memberAccessService = memberAccessService;
    }

    @ModelAttribute
    void requireMemberAccess(@PathVariable Long memberId, Authentication authentication) {
        memberAccessService.requireMemberAccess(authentication, memberId);
    }

    @GetMapping
    public List<PortfolioCandidateAssetResponse> getCandidateAssets(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return portfolioCandidateAssetService.getCandidateAssets(memberId, portfolioId)
                .stream()
                .map(PortfolioCandidateAssetResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<PortfolioCandidateAssetResponse> createCandidateAsset(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @Valid @RequestBody PortfolioCandidateAssetCreateRequest request
    ) {
        PortfolioCandidateAsset candidate = portfolioCandidateAssetService.create(
                memberId,
                portfolioId,
                request.getMarket(),
                request.getTicker(),
                request.getDisplayName()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PortfolioCandidateAssetResponse.from(candidate));
    }

    @DeleteMapping("/{market}/{ticker}")
    public ResponseEntity<Void> deleteCandidateAsset(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Market market,
            @PathVariable String ticker
    ) {
        portfolioCandidateAssetService.delete(memberId, portfolioId, market, ticker);
        return ResponseEntity.noContent().build();
    }
}
